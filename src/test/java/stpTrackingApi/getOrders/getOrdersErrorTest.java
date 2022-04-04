package stpTrackingApi.getOrders;

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
import ru.qa.tinkoff.investTracking.services.SlaveOrder2Dao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.ContractApi;
import ru.qa.tinkoff.swagger.tracking.model.ErrorResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


@Slf4j
@Epic("getOrders - Получение списка заявок, выставляемых от лица ведомого в рамках стратегии")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@Tags({@Tag("stp-tracking-api"), @Tag("getOrders")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class,
})

public class getOrdersErrorTest {

    @Autowired
    StrategyService strategyService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    SlaveOrder2Dao slaveOrder2Dao;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ApiCreator<ContractApi> contractApiCreator;

    String siebelIdMaster;
    String siebelIdSlave;
    String siebelIdNot = "1-2ML9VUT";
    String contractIdSlave;
    String contractIdMaster;
    UUID investIdSlave;
    UUID investIdMaster;
    UUID strategyId;
    UUID idempotencyKey;
    String title;
    String description;


    @BeforeAll
    void getDataFromAccount() {
        siebelIdMaster = stpSiebel.siebelIdApiMaster;
        siebelIdSlave = stpSiebel.siebelIdApiSlave;
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
        });
    }

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

    @BeforeEach
    void getStrategyData() {
        title = "Autotest" + randomNumber(0, 100);
        description = "Autotest getOrders";
        strategyId = UUID.randomUUID();
    }

    private static Stream<Arguments> provideRequiredParam() {
        return Stream.of(
            Arguments.of(null, "5.0", "android"),
            Arguments.of("invest", null, "ios"),
            Arguments.of("invest", "5.0", null)
        );
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideRequiredParam")
    @AllureId("1408354")
    @DisplayName("1408354.getOrders.Получение списка заявок. Не передан один из обязательных параметров")
    @Subfeature("Альтернативные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1408354(String name, String version, String platform) {
        //создаем клиента, контракт и стратегию
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11");
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder(1, 1, 0, 1, instrument.classCodeAAPL, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //вызываем метод getOrders
        ContractApi.GetOrdersOper getOrdersResponse = contractApiCreator.get().getOrders()
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            getOrdersResponse = getOrdersResponse.xAppNameHeader(name);
        }
        if (version != null) {
            getOrdersResponse = getOrdersResponse.xAppVersionHeader(version);
        }
        if (platform != null) {
            getOrdersResponse = getOrdersResponse.xPlatformHeader(platform);
        }
        getOrdersResponse.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getOrdersResponse.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("1413892")
    @DisplayName("1413892.getOrders. Получение списка заявок. Параметр limit = 0")
    @Subfeature("Альтернативные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1413892() {
        //создаем клиента, контракт и стратегию
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11");
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder(1, 1, 0, 1, instrument.classCodeAAPL, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //вызываем метод getOrders
        ErrorResponse getOrdersResponse = contractApiCreator.get().getOrders()
            .xAppNameHeader("invest")
            .xAppVersionHeader("5.0")
            .xPlatformHeader("ios")
            .limitQuery(0)
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response.as(ErrorResponse.class));
        //проверяем полученную ошибку
        assertThat("код ошибки не равно", getOrdersResponse.getErrorCode(), is("Error"));
        assertThat("Сообщение об ошибке не равно", getOrdersResponse.getErrorMessage(), is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("1408312")
    @DisplayName("1408312.getOrders. Получение списка заявок. Не передан x-tcs-siebel-id")
    @Subfeature("Альтернативные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1408312() {
        //создаем клиента, контракт и стратегию
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11");
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder(1, 1, 0, 1, instrument.classCodeAAPL, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //вызываем метод getOrders
        ErrorResponse getOrdersResponse = contractApiCreator.get().getOrders()
            .xAppNameHeader("invest")
            .xAppVersionHeader("5.0")
            .xPlatformHeader("ios")
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.as(ErrorResponse.class));
        //проверяем полученную ошибку
        assertThat("код ошибки не равно", getOrdersResponse.getErrorCode(), is("InsufficientPrivileges"));
        assertThat("Сообщение об ошибке не равно", getOrdersResponse.getErrorMessage(), is("Недостаточно прав"));
    }


    @SneakyThrows
    @Test
    @AllureId("1408717")
    @DisplayName("1408717.getOrders. Получение списка заявок. Не найдена запись в таблице tracking.contract")
    @Subfeature("Альтернативные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1408717() {
        //создаем клиента, контракт и стратегию
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11");
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder(1, 1, 0, 1, instrument.classCodeAAPL, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //вызываем метод getOrders
        ErrorResponse getOrdersResponse = contractApiCreator.get().getOrders()
            .xAppNameHeader("invest")
            .xAppVersionHeader("5.0")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response.as(ErrorResponse.class));
        //проверяем полученную ошибку
        assertThat("код ошибки не равно", getOrdersResponse.getErrorCode(), is("Error"));
        assertThat("Сообщение об ошибке не равно", getOrdersResponse.getErrorMessage(), is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("1408362")
    @DisplayName("1408362.getOrders. Получение списка заявок. Не найден ClientId")
    @Subfeature("Альтернативные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1408362() {
        //создаем клиента, контракт и стратегию
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder(1, 1, 0, 1, instrument.classCodeAAPL, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //вызываем метод getOrders
        ErrorResponse getOrdersResponse = contractApiCreator.get().getOrders()
            .xAppNameHeader("invest")
            .xAppVersionHeader("5.0")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdNot)
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response.as(ErrorResponse.class));
        //проверяем полученную ошибку
        assertThat("код ошибки не равно", getOrdersResponse.getErrorCode(), is("Error"));
        assertThat("Сообщение об ошибке не равно", getOrdersResponse.getErrorMessage(), is("Сервис временно недоступен"));
    }


    ///// методы для тестов getOrders /////

    //метод рандомайза для номера теста
    public static int randomNumber(int min, int max) {
        int number = min + (int) (Math.random() * max);
        return number;
    }

    //метод для создания вставки заявки
    void createSlaveOrder(int minusDays, int minusHours, String contractId, UUID strategyId, int version, int attemptsCount,
                          int action, String classCode, BigDecimal filledQuantity,
                          UUID idempotencyKey, BigDecimal price, BigDecimal quantity, Byte state, String ticker, String tradingClearingAccount) {
        OffsetDateTime createAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(minusDays).minusHours(minusHours);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractId, createAt, strategyId, version, attemptsCount,
            action, classCode, 3, filledQuantity, idempotencyKey,
            UUID.randomUUID(), price, quantity, state,
            ticker, tradingClearingAccount);
    }

    //метод создает записи по заявкам в рамках одной стратегии
    void createTestDataSlaveOrder(int version, int count, int attemptsCounts, int action, String classCode, String ticker, String tradingClearingAccount) {
        idempotencyKey = UUID.randomUUID();
        for (int i = 0; i < count; i++) {
            attemptsCounts = attemptsCounts + 1;
            createSlaveOrder(43, 9, contractIdSlave, strategyId, version, attemptsCounts, action, classCode, new BigDecimal("0"), idempotencyKey, new BigDecimal("173"), new BigDecimal("10"), (byte) 0, ticker, tradingClearingAccount);
        }
    }
}
