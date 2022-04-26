package stpTrackingApi.getSignals;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreator;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioValueDao;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.model.GetOrdersResponse;
import ru.qa.tinkoff.swagger.tracking.model.GetSignalsResponse;
import ru.qa.tinkoff.swagger.tracking.model.Signal;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("getSignals - Получение списка сделок (сигналов) стратегии")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@Tags({@Tag("stp-tracking-api"), @Tag("getSignals")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class,
})
public class GetSignalsTest {

    @Autowired
    StrategyService strategyService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    MasterPortfolioValueDao masterPortfolioValueDao;
    @Autowired
    MasterSignalDao masterSignalDao;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ApiCreator<StrategyApi> strategyApiCreator;

    Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    UUID strategyId;
    String siebelIdMaster;
    String siebelIdSlave;
    final String tickerNotFound = "TESTTEST";
    final String tradingClearingAccountNotFound = "TKCBM_TCAB";
    String quantityFXDE = "5";
    String quantitySU29009RMFS6 = "7";
    String title;
    String description;
    UUID investIdMaster;
    UUID investIdSlave;

    @BeforeAll
    void conf() {
        siebelIdMaster = stpSiebel.siebelIdApiMaster;
        siebelIdSlave = stpSiebel.siebelIdMasterStpTrackingMaster;
        title = steps.getTitleStrategy();
        description = "стратегия autotest GetSignals";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные от сервиса счетов о slave
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(contractIdSlave, investIdSlave);
        steps.deleteDataFromDb(contractIdMaster, investIdMaster);
    }

    @BeforeEach
    void createClient() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(32), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11");
    }


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
                masterSignalDao.deleteMasterSignalByStrategy(strategyId);
            } catch (Exception e) {
            }
        });
    }

    private static Stream<Arguments> provideRequiredParam() {
        return Stream.of(
            Arguments.of(null, "4.5.6", "android"),
            Arguments.of("trading-invest", null, "android"),
            Arguments.of("trading-invest", "4.5.6", null)
        );
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideRequiredParam")
    @AllureId("1309488")
    @DisplayName("1309488.GetSignals.Валидация входного запроса")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C1309488(String name, String version, String platform) {
        //вызываем метод для получения списка сделок (сигналов) стратегии
        StrategyApi.GetSignalsOper getSignals = strategyApiCreator.get().getSignals()
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            getSignals = getSignals.xAppNameHeader(name);
        }
        if (version != null) {
            getSignals = getSignals.xAppVersionHeader(version);
        }
        if (platform != null) {
            getSignals = getSignals.xPlatformHeader(platform);
        }
        //получаем ответ и проверяем errorCode и Error ошибки
        getSignals.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getSignals.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("1309491")
    @DisplayName("C1309491.GetSignals.Заголовок x-tcs-siebel-id не передан")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C1309491() {
        //вызываем метод для получения списка сделок (сигналов) стратегии
        StrategyApi.GetSignalsOper getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .respSpec(spec -> spec.expectStatusCode(401));
        //получаем ответ и проверяем errorCode и Error ошибки
        getSignals.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getSignals.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("InsufficientPrivileges"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно прав"));
    }


    @SneakyThrows
    @Test
    @AllureId("1309550")
    @DisplayName("C1309550.GetSignals.Не удалось получить clientId из кеш clientIdCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C1309550() {
        //вызываем метод для получения списка сделок (сигналов) стратегии
        StrategyApi.GetSignalsOper getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader("6-RGHKKZA6")
            .respSpec(spec -> spec.expectStatusCode(422));
        //получаем ответ и проверяем errorCode и Error ошибки
        getSignals.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getSignals.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("1309580")
    @DisplayName("C1309580.GetSignals.Не найдена стратегия")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C1309580() {
        //вызываем метод для получения списка сделок (сигналов) стратегии
        StrategyApi.GetSignalsOper getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(UUID.randomUUID())
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .respSpec(spec -> spec.expectStatusCode(422));
        //получаем ответ и проверяем errorCode и Error ошибки
        getSignals.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getSignals.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("1309585")
    @DisplayName("C1309585.GetSignals.Пользователю clientId не доступен метод")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C1309585() {
        //вызываем метод для получения списка сделок (сигналов) стратегии
        StrategyApi.GetSignalsOper getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader("5-B4QTU240")
            .respSpec(spec -> spec.expectStatusCode(422));
        //получаем ответ и проверяем errorCode и Error ошибки
        getSignals.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getSignals.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("1311287")
    @DisplayName("C1311287.GetSignals.Запрос от slave в статусе inactive")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C1311287() {
        //создаем подписку для slave c заблокированной подпиской
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(2);
        OffsetDateTime endSubTime = OffsetDateTime.now().minusDays(1);
        steps.createSubcriptionDraftOrInActive(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave, null, ContractState.untracked,
            strategyId, SubscriptionStatus.inactive, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            new java.sql.Timestamp(endSubTime.toInstant().toEpochMilli()), false);
        //вызываем метод для получения списка сделок (сигналов) стратегии
        StrategyApi.GetSignalsOper getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .respSpec(spec -> spec.expectStatusCode(422));
        //получаем ответ и проверяем errorCode и Error ошибки
        getSignals.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getSignals.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("1309614")
    @DisplayName("C1309614.GetSignals.Не найдены сигналы в табл. master_signal")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C1309614() {
        //вызываем метод для получения списка сделок (сигналов) стратегии
        GetSignalsResponse getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetSignalsResponse.class));
        //получаем ответ и проверяем что ответ пустой
        assertThat("размер Items не равно", getSignals.getItems().size(), is(0));
    }

    @SneakyThrows
    @Test
    @AllureId("1311471")
    @DisplayName("C1311471.GetSignals.Значение по позиции не найдено в exchangePositionCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C1311471() {
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = createListMasterPositionNotFoundExPosCach(steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterPos, 3, "6259.17", date);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignalNotFoundExPosCac(strategyId);
        //вызываем метод для получения списка сделок (сигналов) стратегии
        GetSignalsResponse getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetSignalsResponse.class));
        //смотрим сигналы в master_signal
        List<MasterSignal> masterSignal = masterSignalDao.getAllMasterSignal(strategyId);
        List<MasterSignal> signal = masterSignal.stream().filter(res -> res.getTicker().equals(instrument.tickerNOK))
            .collect(Collectors.toList());
        //получаем ответ и проверяем
        assertThat("размер items не равно", getSignals.getItems().size(), is(1));
        assertThat("totalAmount последнего сигнала не равно", getSignals.getItems().get(0).getTotalAmount(),
            is(signal.get(0).getPrice().multiply(signal.get(0).getQuantity()).setScale(2, RoundingMode.HALF_UP)));
        assertThat("createdAt последнего сигнала не равно", getSignals.getItems().get(0).getCreatedAt().toInstant(),
            is(signal.get(0).getCreatedAt().toInstant()));
        assertThat("price последнего сигнала не равно", getSignals.getItems().get(0).getPrice().getValue(),
            is(signal.get(0).getPrice().setScale(2, RoundingMode.HALF_UP)));
        assertThat("currency последнего сигнала не равно", getSignals.getItems().get(0).getPrice().getCurrency().getValue(),
            is("usd"));
        assertThat("ticker последнего сигнала не равно", getSignals.getItems().get(0).getExchangePosition().getTicker(),
            is(signal.get(0).getTicker()));
    }


    @SneakyThrows
    @Test
    @AllureId("1309648")
    @DisplayName("C1309648.GetSignals.Запрос от мастера")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C1309648() {
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = createListMasterPosition(steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterPos, 10, "6259.17", date);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignal(strategyId);
        //вызываем метод для получения списка сделок (сигналов) стратегии
        GetSignalsResponse getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetSignalsResponse.class));
        //смотрим сигналы в master_signal
        List<MasterSignal> masterSignal = masterSignalDao.getAllMasterSignal(strategyId);
        //получаем ответ и проверяем
        checkParam(masterSignal, getSignals);
    }

    @SneakyThrows
    @Test
    @AllureId("1311161")
    @DisplayName("C1311161.GetSignals.Запрос от salve в статусе active")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C1311161() {
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = createListMasterPosition(steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterPos, 10, "6259.17", date);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignal(strategyId);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вызываем метод для получения списка сделок (сигналов) стратегии
        GetSignalsResponse getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetSignalsResponse.class));
        //смотрим сигналы в master_signal
        List<MasterSignal> masterSignal = masterSignalDao.getAllMasterSignal(strategyId);
        //получаем ответ и проверяем
        checkParam(masterSignal, getSignals);
    }


    @SneakyThrows
    @Test
    @AllureId("1814799")
    @DisplayName("1814799 GetSignals.Запрос от slave. subscription.blocked = true")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C1814799() {
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = createListMasterPosition(steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterPos, 10, "6259.17", date);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignal(strategyId);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, true, false);
        //вызываем метод для получения списка сделок (сигналов) стратегии
        GetSignalsResponse getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetSignalsResponse.class));
        //проверяем что вернулся пустой список сигналов
        assertThat("items != []", getSignals.getItems().toString(), is("[]"));
    }


    @SneakyThrows
    @Test
    @AllureId("1311326")
    @DisplayName("C1311326.GetSignals.Запрос от slave в статусе draft")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C1311326() {
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = createListMasterPosition(steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterPos, 10, "6259.17", date);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignal(strategyId);
        //создаем подписку для slave c заблокированной подпиской
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(2);
        steps.createSubcriptionDraftOrInActive(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave, null, ContractState.untracked,
            strategyId, SubscriptionStatus.draft, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод для получения списка сделок (сигналов) стратегии
        GetSignalsResponse getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetSignalsResponse.class));
        //смотрим сигналы в master_signal
        List<MasterSignal> masterSignal = masterSignalDao.getAllMasterSignal(strategyId);
        //получаем ответ и проверяем
        checkParam(masterSignal, getSignals);
    }


    @SneakyThrows
    @Test
    @AllureId("1312164")
    @DisplayName("C1312164.GetSignals.Проверка инструментов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C1312164() {
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = createListMasterPositionOther(steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterPos, 4, "6259.17", date);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignalOther(strategyId);
        //создаем подписку для slave c заблокированной подпиской
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(2);
        steps.createSubcriptionDraftOrInActive(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave, null, ContractState.untracked,
            strategyId, SubscriptionStatus.draft, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод для получения списка сделок (сигналов) стратегии
        GetSignalsResponse getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetSignalsResponse.class));
        //смотрим сигналы в master_signal
        List<MasterSignal> masterSignal = masterSignalDao.getAllMasterSignal(strategyId);
        List<MasterSignal> signalBondDB = masterSignal.stream().filter(res -> res.getTicker().equals(instrument.tickerSU29009RMFS6))
            .collect(Collectors.toList());
        List<MasterSignal> signalEtfDB = masterSignal.stream().filter(res -> res.getTicker().equals(instrument.tickerFXDE))
            .collect(Collectors.toList());
        List<MasterSignal> signalMoneyDB = masterSignal.stream().filter(res -> res.getTicker().equals(instrument.tickerUSD))
            .collect(Collectors.toList());
        List<Signal> signalBondReq = getSignals.getItems()
            .stream().filter(res -> res.getExchangePosition().getTicker().equals(instrument.tickerSU29009RMFS6))
            .collect(Collectors.toList());
        List<Signal> signalEtfReq = getSignals.getItems()
            .stream().filter(res -> res.getExchangePosition().getTicker().equals(instrument.tickerFXDE))
            .collect(Collectors.toList());
        List<Signal> signalMoneyReq = getSignals.getItems()
            .stream().filter(res -> res.getExchangePosition().getTicker().equals(instrument.tickerUSD))
            .collect(Collectors.toList());
        checkInstrumentParam(signalBondDB, signalBondReq, instrument.briefNameSU29009RMFS6, instrument.imageSU29009RMFS6, instrument.typeSU29009RMFS6, 3);
        checkInstrumentParam(signalEtfDB, signalEtfReq, instrument.briefNameFXDE, instrument.imageFXDE, instrument.typeFXDE, 2);
//        checkInstrumentParam(signalMoneyDB, signalMoneyReq, briefNameMoney, imageMoney, typeMoney, 4);
    }


    @SneakyThrows
    @Test
    @AllureId("1311186")
    @DisplayName("C1311186.GetSignals.Параметры ответа")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C1311186() {
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = createListMasterPosition(steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterPos, 10, "6259.17", date);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignal(strategyId);
        //вызываем метод для получения списка сделок (сигналов) стратегии
        GetSignalsResponse getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .limitQuery(2)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetSignalsResponse.class));
        //смотрим сигналы в master_signal
        List<MasterSignal> masterSignal = masterSignalDao.getAllMasterSignal(strategyId);
        //получаем ответ и проверяем
        assertThat("hasNext не равно", getSignals.getHasNext(), is(true));
        assertThat("totalAmount последнего сигнала не равно", getSignals.getItems().get(0).getTotalAmount(),
            is(masterSignal.get(0).getPrice().multiply(masterSignal.get(0).getQuantity()).setScale(2, RoundingMode.HALF_UP)));
        assertThat("createdAt последнего сигнала не равно", getSignals.getItems().get(0).getCreatedAt().toInstant(),
            is(masterSignal.get(0).getCreatedAt().toInstant()));
        assertThat("quantity последнего сигнала не равно", getSignals.getItems().get(0).getQuantity(),
            is(masterSignal.get(0).getQuantity()));
        assertThat("action последнего сигнала не равно", getSignals.getItems().get(0).getAction().getValue(),
            is("buy"));
        assertThat("price последнего сигнала не равно", getSignals.getItems().get(0).getPrice().getValue(),
            is(masterSignal.get(0).getPrice().setScale(2, RoundingMode.HALF_UP)));
        assertThat("currency последнего сигнала не равно", getSignals.getItems().get(0).getPrice().getCurrency().getValue(),
            is("usd"));
        assertThat("ticker последнего сигнала не равно", getSignals.getItems().get(0).getExchangePosition().getTicker(),
            is(masterSignal.get(0).getTicker()));
        assertThat("briefName последнего сигнала не равно", getSignals.getItems().get(0).getExchangePosition().getBriefName(),
            is(instrument.briefNameNOK));
        assertThat("image последнего сигнала не равно", getSignals.getItems().get(0).getExchangePosition().getImage(),
            is(instrument.imageNOK));
        assertThat("type последнего сигнала не равно", getSignals.getItems().get(0).getExchangePosition().getType().getValue(),
            is(instrument.typeNOK));
        assertThat("version последнего сигнала не равно", getSignals.getItems().get(0).getVersion(),
            is(10));
    }


    private static Stream<Arguments> provideLimitParam() {
        return Stream.of(
            Arguments.of(2, true),
            Arguments.of(3, true),
            Arguments.of(6, true),
            Arguments.of(9, false)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideLimitParam")
    @AllureId("945567")
    @DisplayName("C945567.GetSignals.Указан только limit")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C945567(int limit, Boolean hasNext) {
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = createListMasterPosition(steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterPos, 10, "6259.17", date);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignal(strategyId);
        //вызываем метод для получения списка сделок (сигналов) стратегии
        GetSignalsResponse getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .limitQuery(limit)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetSignalsResponse.class));
        //получаем ответ и проверяем
        assertThat("hasNext не равно", getSignals.getHasNext(), is(hasNext));
        assertThat("размер items не равно", getSignals.getItems().size(), is(limit));
    }


    private static Stream<Arguments> provideCursorParam() {
        return Stream.of(
            Arguments.of(3, false),
            Arguments.of(6, false),
            Arguments.of(9, false)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideCursorParam")
    @AllureId("945566")
    @DisplayName("C945566.GetSignals.Указан только cursor")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C945566(int cursor, Boolean hasNext) {
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = createListMasterPosition(steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterPos, 10, "6259.17", date);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignal(strategyId);
        //вызываем метод для получения списка сделок (сигналов) стратегии
        GetSignalsResponse getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .cursorQuery(cursor)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetSignalsResponse.class));
        //смотрим сигналы в master_signal
        List<MasterSignal> masterSignal = masterSignalDao.getMasterSignalWithCursor(strategyId, cursor);
        //получаем ответ и проверяем
        assertThat("hasNext не равно", getSignals.getHasNext(), is(hasNext));
        assertThat("размер items не равно", getSignals.getItems().size(), is(masterSignal.size()));
    }


    private static Stream<Arguments> provideCursorLimitParam() {
        return Stream.of(
            Arguments.of(3, 2),
            Arguments.of(7, 4),
            Arguments.of(9, 5),
            Arguments.of(10, 3)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideCursorLimitParam")
    @AllureId("945547")
    @DisplayName("C945547.GetSignals.С указанием cursor & limit")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C945547(int cursor, int limit) {
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = createListMasterPosition(steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterPos, 10, "6259.17", date);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignal(strategyId);
        //вызываем метод для получения списка сделок (сигналов) стратегии
        GetSignalsResponse getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .cursorQuery(cursor)
            .limitQuery(limit)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetSignalsResponse.class));
        //смотрим сигналы в master_signal
        List<MasterSignal> masterSignal = masterSignalDao.getMasterSignalWithCursorAndLimit(strategyId, cursor, limit);
        //получаем ответ и проверяем
        assertThat("totalAmount последнего сигнала не равно", getSignals.getItems().get(0).getTotalAmount(),
            is(masterSignal.get(0).getPrice().multiply(masterSignal.get(0).getQuantity()).setScale(2, RoundingMode.HALF_UP)));
        assertThat("createdAt последнего сигнала не равно", getSignals.getItems().get(0).getCreatedAt().toInstant(),
            is(masterSignal.get(0).getCreatedAt().toInstant()));
        assertThat("price последнего сигнала не равно", getSignals.getItems().get(0).getPrice().getValue(),
            is(masterSignal.get(0).getPrice().setScale(2, RoundingMode.HALF_UP)));
        assertThat("currency последнего сигнала не равно", getSignals.getItems().get(0).getPrice().getCurrency().getValue(),
            is("usd"));
        assertThat("ticker последнего сигнала не равно", getSignals.getItems().get(0).getExchangePosition().getTicker(),
            is(masterSignal.get(0).getTicker()));
        assertThat("version последнего сигнала не равно", getSignals.getItems().get(0).getVersion(),
            is(masterSignal.get(0).getVersion()));

    }

    @SneakyThrows
    @Test
    @AllureId("1311830")
    @DisplayName("C1311830.GetSignals.Проверка version")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения списка сделок (сигналов) по торговой стратегии от новых к старым.")
    void C1311830() {
        //создаем список позиций в портфеле мастера
        List<MasterPortfolio.Position> masterPos = createListMasterPosition(steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        //создаем запись в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        steps.createMasterPortfolio(contractIdMaster, strategyId, masterPos, 10, "6259.17", date);
        //создаем записи по сигналу на разные позиции
        createTestDateToMasterSignal(strategyId);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,  ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вызываем метод для получения списка сделок (сигналов) стратегии
        GetSignalsResponse getSignals = strategyApiCreator.get().getSignals()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetSignalsResponse.class));
        //смотрим сигналы в master_signal
        List<MasterSignal> masterSignal = masterSignalDao.getAllMasterSignal(strategyId);
        //получаем ответ и проверяем
        for (int i = 0; i < masterSignal.size(); i++) {
            assertThat("version сигнала не равно", getSignals.getItems().get(i).getVersion(),
                is(masterSignal.get(i).getVersion()));
        }
    }


    void checkParam(List<MasterSignal> masterSignal, GetSignalsResponse getSignals) {
        assertThat("nextCursor не равно", getSignals.getNextCursor(), is(masterSignal.get(masterSignal.size() - 1).getVersion().toString()));
        assertThat("hasNext не равно", getSignals.getHasNext(), is(false));
        assertThat("размер items не равно", getSignals.getItems().size(), is(masterSignal.size()));
    }

    void checkInstrumentParam(List<MasterSignal> masterSignal, List<Signal> signal, String briefName, String image, String type, int version) {
        //получаем ответ и проверяем
        assertThat("totalAmount последнего сигнала не равно", signal.get(0).getTotalAmount(),
            is(masterSignal.get(0).getPrice().multiply(masterSignal.get(0).getQuantity()).setScale(2, RoundingMode.HALF_UP)));
        assertThat("createdAt последнего сигнала не равно", signal.get(0).getCreatedAt().toInstant(),
            is(masterSignal.get(0).getCreatedAt().toInstant()));
        assertThat("quantity последнего сигнала не равно", signal.get(0).getQuantity(),
            is(masterSignal.get(0).getQuantity()));
        assertThat("action последнего сигнала не равно", signal.get(0).getAction().getValue(),
            is("buy"));
        assertThat("price последнего сигнала не равно", signal.get(0).getPrice().getValue(),
            is(masterSignal.get(0).getPrice().setScale(2, RoundingMode.HALF_UP)));
        assertThat("currency последнего сигнала не равно", signal.get(0).getPrice().getCurrency().getValue(),
            is("rub"));
        assertThat("ticker последнего сигнала не равно", signal.get(0).getExchangePosition().getTicker(),
            is(masterSignal.get(0).getTicker()));
        assertThat("briefName последнего сигнала не равно", signal.get(0).getExchangePosition().getBriefName(),
            is(briefName));
        assertThat("image последнего сигнала не равно", signal.get(0).getExchangePosition().getImage(),
            is(image));
        assertThat("type последнего сигнала не равно", signal.get(0).getExchangePosition().getType().getValue(),
            is(type));
        assertThat("version последнего сигнала не равно", signal.get(0).getVersion(),
            is(version));
    }


    //методы создает записи по сигналам стратегии
    void createTestDateToMasterSignal(UUID strategyId) {
        createMasterSignal(31, 1, 2, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "4.07", "4", 12);
        createMasterSignal(30, 2, 3, strategyId, instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "90.18", "6", 11);
        createMasterSignal(29, 2, 4, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.98", "7", 12);
        createMasterSignal(5, 4, 5, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.81", "1", 12);
        createMasterSignal(4, 2, 6, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "107.81", "1", 12);
        createMasterSignal(3, 1, 7, strategyId, instrument.tickerABBV, instrument.tradingClearingAccountABBV,
            "90.18", "3", 11);
        createMasterSignal(2, 1, 8, strategyId, instrument.tickerXS0191754729, instrument.tradingClearingAccountXS0191754729,
            "190.18", "1", 12);
        createMasterSignal(0, 2, 9, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.17", "4", 12);
        createMasterSignal(0, 1, 10, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.09", "4", 12);
    }


    //методы создает записи по сигналам стратегии
    void createTestDateToMasterSignalNotFoundExPosCac(UUID strategyId) {
        createMasterSignal(0, 1, 2, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "3.09", "19", 12);
        createMasterSignal(0, 1, 3, strategyId, tickerNotFound, tradingClearingAccountNotFound,
            "3.09", "3", 12);

    }


    //методы создает записи по сигналам стратегии
    void createTestDateToMasterSignalOther(UUID strategyId) {
        createMasterSignal(31, 1, 2, strategyId, instrument.tickerFXDE, instrument.tradingClearingAccountFXDE,
            "4.07", quantityFXDE, 12);
        createMasterSignal(30, 2, 3, strategyId, instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6,
            "90.18", quantitySU29009RMFS6, 12);
    }

    void createMasterSignal(int minusDays, int minusHours, int version, UUID strategyId, String ticker, String tradingClearingAccount,
                            String price, String quantity, int action) {
        LocalDateTime time = LocalDateTime.now().minusDays(minusDays).minusHours(minusHours);
        Date convertedDatetime = Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
        MasterSignal masterSignal = MasterSignal.builder()
            .strategyId(strategyId)
            .version(version)
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .action((byte) action)
            .state((byte) 1)
            .price(new BigDecimal(price))
            .quantity(new BigDecimal(quantity))
            .createdAt(convertedDatetime)
            .build();
        masterSignalDao.insertIntoMasterSignal(masterSignal);
    }


    public List<MasterPortfolio.Position> createListMasterPosition(Tracking.Portfolio.Position position) {
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerNOK)
            .tradingClearingAccount(instrument.tradingClearingAccountNOK)
            .quantity(new BigDecimal("19"))
            .changedAt(Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1).toInstant()))
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerABBV)
            .tradingClearingAccount(instrument.tradingClearingAccountABBV)
            .quantity(new BigDecimal("3"))
            .changedAt(Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).minusHours(1).toInstant()))
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerAAPL)
            .tradingClearingAccount(instrument.tradingClearingAccountAAPL)
            .quantity(new BigDecimal("2"))
            .changedAt(Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(4).minusHours(2).toInstant()))
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerXS0191754729)
            .tradingClearingAccount(instrument.tradingClearingAccountXS0191754729)
            .quantity(new BigDecimal("1"))
            .changedAt(Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(2).minusHours(1).toInstant()))
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        return positionList;
    }


    public List<MasterPortfolio.Position> createListMasterPositionOther(Tracking.Portfolio.Position position) {
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerFXDE)
            .tradingClearingAccount(instrument.tradingClearingAccountFXDE)
            .quantity(new BigDecimal(quantityFXDE))
            .changedAt(Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1).toInstant()))
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal(quantitySU29009RMFS6))
            .changedAt(Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).minusHours(1).toInstant()))
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        return positionList;
    }

    public List<MasterPortfolio.Position> createListMasterPositionNotFoundExPosCach(Tracking.Portfolio.Position position) {
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerNOK)
            .tradingClearingAccount(instrument.tradingClearingAccountNOK)
            .quantity(new BigDecimal("19"))
            .changedAt(Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1).toInstant()))
            .lastChangeDetectedVersion(4)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerNotFound)
            .tradingClearingAccount(tradingClearingAccountNotFound)
            .quantity(new BigDecimal("3"))
            .changedAt(Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).minusHours(1).toInstant()))
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        return positionList;
    }


}
