package stpTrackingApi.getOrders;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
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
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.ContractApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.GetOrdersResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
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
    StpTrackingSlaveStepsConfiguration.class
})

public class getOrdersTest {

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
    StpTrackingSlaveSteps slaveSteps;
    @Autowired
    SlaveOrderDao slaveOrderDao;


    String siebelIdMaster = "5-F6VT91I0";
    String siebelIdSlave = "4-M3KKMT7";

    String contractIdSlave;
    String contractIdMaster;

    UUID investIdSlave;
    UUID investIdMaster;
    UUID strategyId;
    UUID idempotencyKey;

    String ticker = "AAPL";
    String classCode = "SPBXM";
    String tradingClearingAccount = "TKCBM_TCAB";

    String ticker1 = "SBERT";
    String classCode1 = "TQBR";
    String tradingClearingAccount1 = "L01+00002F00";

    String ticker2 = "AFX@DE";
    String classCode2 = "SPBDE";
    String tradingClearingAccount2 = "L01+00000SPB";

    String ticker3 = "TCSG";
    String classCode3 = "TQBR";
    String tradingClearingAccount3 = "L01+00000F00";

    String ticker4 = "TBIO";
    String classCode4 = "TQTF";
    String tradingClearingAccount4 = "NDS000000001";

    String title;
    String description;

    Integer maxLimit = 100;
    Integer defaultLimit = 30;

    ContractApi contractApi = ApiClient.api(ApiClient.Config.apiConfig()).contract();

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
                slaveOrderDao.deleteSlaveOrder(contractIdSlave,strategyId);
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
    void getStrategyData(){
        title = "Autotest" + randomNumber(0,100);
        description = "Autotest getOrders";
        strategyId = UUID.randomUUID();
    }

    @SneakyThrows
    @Test
    @AllureId("1416878")
    @DisplayName("1416878.getOrders. Получение списка заявок. Успешное получение списка. limit не передан")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1416878(){
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave, null,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder(1,127, 0,1, classCode, ticker, tradingClearingAccount);
        //вызываем метод getOrders, получаем ответ и проверяем
        GetOrdersResponse getOrdersResponse = getOrders();
        List<SlaveOrder> getListFromSlaveOrder = slaveOrderDao.findSlaveOrderLimit(contractIdSlave, strategyId,30);

        List<ru.qa.tinkoff.swagger.tracking.model.Order> getItemsList = new ArrayList<>();
        List<SlaveOrder> getItemsListFromDb = new ArrayList<>();

        String action;

        assertThat("количество заявок не равно", getOrdersResponse.getItems().size(), is(defaultLimit));
        assertThat("hasNext не равен", getOrdersResponse.getHasNext(), is(true));
        for (int i = 0; i < getOrdersResponse.getItems().size(); i++) {

            getItemsList.add(getOrdersResponse.getItems().get(i));
            getItemsListFromDb.add(getListFromSlaveOrder.get(i));

            assertThat("ticker не равен " + getItemsList.get(i).getExchangePosition().getTicker(),
                getItemsList.get(i).getExchangePosition().getTicker(), is(getItemsListFromDb.get(i).getTicker()));
            assertThat("price не равен " + getItemsList.get(i).getPrice().getValue().toString(),
                getItemsList.get(i).getPrice().getValue().toString(), is(getItemsListFromDb.get(i).getPrice().toString()));
            assertThat("quantity не равен "+ getItemsList.get(i).getQuantity(), getItemsList.get(i).getQuantity(),
                is(getItemsListFromDb.get(i).getQuantity()));
            assertThat("created at " + getItemsList.get(i).getCreatedAt().toInstant().atOffset(ZoneOffset.UTC), getItemsList.get(i).getCreatedAt().toInstant().atOffset(ZoneOffset.UTC),
                is(getItemsListFromDb.get(i).getCreateAt().toInstant().atOffset(ZoneOffset.UTC)));
            if (getItemsListFromDb.get(i).getAction().intValue() == 1) {
                action = "sell";
            } else {
                action = "buy";
            }
            assertThat("action не равен " + getItemsList.get(i).getAction(), getItemsList.get(i).getAction().toString(), is(action));
        }
    }


    private static Stream<Arguments> limitParam() {
        return Stream.of(
            Arguments.of(1, true),
            Arguments.of(99, true),
            Arguments.of(100, true),
            Arguments.of(101, true)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("limitParam")
    @AllureId("1412296")
    @DisplayName("1412296.getOrders. Получение списка заявок. Проверка параметра max-limit")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1412296(int limit, boolean hasNext){
        //создаем клиента, контракт и стратегию
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave, null,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
       createTestDataSlaveOrder(1,127, 0,1, classCode, ticker, tradingClearingAccount);
        //вызываем метод getOrders
        GetOrdersResponse getOrdersResponse = contractApi.getOrders()
            .xAppNameHeader("invest")
            .xAppVersionHeader("5.0")
            .xPlatformHeader("ios")
            .limitQuery(limit)
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetOrdersResponse.class));
        //получаем ответ и проверяем
        assertThat("hasNext не равно", getOrdersResponse.getHasNext(), is(hasNext));
        if (limit > maxLimit) {
            assertThat("размер items не равно", getOrdersResponse.getItems().size(), is(maxLimit));
        } else {
            assertThat("размер items не равно", getOrdersResponse.getItems().size(), is(limit));
        }
    }

    @SneakyThrows
    @Test
    @AllureId("1420761")
    @DisplayName("1420761.getOrders. Получение списка заявок. Нет записей о выставленных заявках")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1420761() {
        //создаем клиента, контракт и стратегию
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave, null,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вызываем метод getOrders, получаем ответ и проверяем
        GetOrdersResponse getDataOrders = getOrders();
        assertThat("hasNext не равно", getDataOrders.getHasNext(), is(false));
        assertThat("items != []", getDataOrders.getItems().toString(), is("[]"));
    }


    @SneakyThrows
    @Test
    @AllureId("1416252")
    @DisplayName("1416252.getOrders. Получение списка заявок. В exchangePositionCache не найдены значения позиции из заявки")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1416252() {
        //создаем клиента, контракт и стратегию
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave, null,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder(1, 1, 125,1, classCode, ticker, tradingClearingAccount);
        createTestDataSlaveOrder(2, 3, 0,1, classCode1, ticker1, tradingClearingAccount1);
        //вызываем метод getOrders, получаем ответ и проверяем
        GetOrdersResponse getDataOrders = getOrders();
        assertThat("items не равно", getDataOrders.getItems().size(), is(1));
        assertThat("hasNext не равен", getDataOrders.getHasNext(), is(false));
        assertThat("cursor не равен", getDataOrders.getNextCursor(), is("1_126"));
    }

    @SneakyThrows
    @Test
    @AllureId("1411778")
    @DisplayName("1411778. getOrders. Получение списка заявок. Slave не подписан на стратегию")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1411778() {
        //создаем клиента, контракт и стратегию
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        //создаём запись о клиенте без подписки на стратегию
        slaveSteps.createClientWithContract(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,
            null, ContractState.untracked, null);
        //вызываем метод getOrders, получаем ответ и проверяем
        GetOrdersResponse getDataOrders = getOrders();
        assertThat("items != []", getDataOrders.getItems().toString(), is("[]"));
    }

    @SneakyThrows
    @Test
    @AllureId("1415416")
    @DisplayName("1415416. getOrders. Получение списка заявок. Cursor передан")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1415416() {
        //создаем клиента, контракт и стратегию
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave, null,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder(1, 10,1,0, classCode, ticker, tradingClearingAccount);
        //вызываем метод getOrders
        GetOrdersResponse getOrdersResponse = contractApi.getOrders()
            .xAppNameHeader("invest")
            .xAppVersionHeader("5.0")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdPath(contractIdSlave)
            .cursorQuery("1_8")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetOrdersResponse.class));
        //получаем ответ и проверяем
        assertThat("cursor не равен", getOrdersResponse.getNextCursor(), is("1_2"));
        assertThat("items не равен", getOrdersResponse.getItems().size(), is(6));
        assertThat("hasNext не равен", getOrdersResponse.getHasNext(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1413907")
    @DisplayName("1413907. getOrders. Получение списка заявок. Cursor не передан")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1413907() {
        //создаем клиента, контракт и стратегию
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave, null,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder(1, 10,1,0, classCode, ticker, tradingClearingAccount);
        //вызываем метод getOrders, получаем ответ и проверяем
        GetOrdersResponse getDataOrders = getOrders();
        assertThat("cursor не равен", getDataOrders.getNextCursor(), is("1_2"));
        assertThat("items не равен", getDataOrders.getItems().size(), is(10));
        assertThat("hasNext не равен", getDataOrders.getHasNext(), is(false));
    }



    @SneakyThrows
    @Test
    @AllureId("1416327")
    @DisplayName("1416327. getOrders. Получение списка заявок. Currency недопустимой валюты")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1416327() {
        //создаем клиента, контракт и стратегию
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave, null,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder(1, 10,1,1, classCode2, ticker2, tradingClearingAccount2);
        createTestDataSlaveOrder(2, 1,0,1, classCode, ticker, tradingClearingAccount);
        //вызываем метод getOrders, получаем ответ и проверяем
        GetOrdersResponse getDataOrders = getOrders();
        assertThat("cursor не равен", getDataOrders.getNextCursor(), is("1_2"));
        assertThat("items не равен", getDataOrders.getItems().size(), is(1));
        assertThat("ticker не равен", getDataOrders.getItems().get(0).getExchangePosition().getTicker(), is(ticker));
        assertThat("hasNext не равен", getDataOrders.getHasNext(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1416268")
    @DisplayName("1416268. getOrders. Получение списка заявок. Action не совпадает")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1416268() {
        //создаем клиента, контракт и стратегию
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave, null,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder(1, 1,0,1, classCode, ticker, tradingClearingAccount);
        createTestDataSlaveOrder(2, 1,0,3, classCode, ticker, tradingClearingAccount);
        //вызываем метод getOrders, получаем ответ и проверяем
        GetOrdersResponse getDataOrders = getOrders();
        assertThat("cursor не равен", getDataOrders.getNextCursor(), is("1_1"));
        assertThat("items не равен", getDataOrders.getItems().size(), is(1));
        assertThat("ticker не равен", getDataOrders.getItems().get(0).getExchangePosition().getTicker(), is(ticker));
        assertThat("hasNext не равен", getDataOrders.getHasNext(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1578204")
    @DisplayName("1578204. getOrders. Проверка корректности отображения параметра issuer.isAffiliated.")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1578204() {
        //создаем клиента, контракт и стратегию
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave, null,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder(1, 1,0,1, classCode3, ticker3, tradingClearingAccount3);
        createTestDataSlaveOrder(2, 1,0,1, classCode4, ticker4, tradingClearingAccount4);
        createTestDataSlaveOrder(3, 1,0,1, classCode, ticker, tradingClearingAccount);
        //вызываем getOrders, получаем ответ и проверяем
        GetOrdersResponse getDataOrders = getOrders();
        assertThat("cursor не равен", getDataOrders.getNextCursor(), is("1_1"));
        assertThat("items не равен", getDataOrders.getItems().size(), is(3));
        assertThat("isAffiliated не равен", getDataOrders.getItems().get(0).getIssuer().getIsAffiliated(), is(false));
        assertThat("isAffiliated не равен", getDataOrders.getItems().get(1).getIssuer().getIsAffiliated(), is(true));
        assertThat("isAffiliated не равен", getDataOrders.getItems().get(2).getIssuer().getIsAffiliated(), is(true));
        assertThat("hasNext не равен", getDataOrders.getHasNext(), is(false));
    }




    ///// методы для тестов getOrders /////

    //метод рандомайза для номера теста
    public static int randomNumber(int min, int max) {
        int number = min + (int) (Math.random() * max);
        return number;
    }

    //метод для создания вставки заявки
    void createSlaveOrder(int minusDays, int minusHours, String contractId, UUID strategyId, int version, int attemptsCount,
                            int action, String classCode, int filledQuantity,
                            UUID idempotencyKey, String price, String quantity, int state, String ticker, String tradingClearingAccount) {
        LocalDateTime time = LocalDateTime.now().minusDays(minusDays).minusHours(minusHours);
        Date convertedDatetime = Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
        SlaveOrder slaveOrder = SlaveOrder.builder()
            .contractId(contractId)
            .strategyId(strategyId)
            .version(version)
            .attemptsCount((byte) attemptsCount)
            .action((byte) action)
            .classCode(classCode)
            .filledQuantity(new BigDecimal (filledQuantity))
            .idempotencyKey(idempotencyKey)
            .price(new BigDecimal(price))
            .quantity(new BigDecimal(quantity))
            .state((byte) 0)
            .tradingClearingAccount(tradingClearingAccount)
            .ticker(ticker)
            .createAt(convertedDatetime)
            .build();
        slaveOrderDao.insertSlaveOrder(slaveOrder);
    }

    //метод создает записи по заявкам в рамках одной стратегии
    void createTestDataSlaveOrder(int version, int count, int attemptsCounts, int action, String classCode, String ticker, String tradingClearingAccount) {
        idempotencyKey = UUID.randomUUID();
        for(int i=0; i<count; i++) {
            attemptsCounts = attemptsCounts + 1;
            createSlaveOrder(43, 9, contractIdSlave, strategyId, version, attemptsCounts, action, classCode, 0, idempotencyKey, "173", "1", 0, ticker, tradingClearingAccount);
        }
    }

    //метод получает список заявок slave
    public GetOrdersResponse getOrders(){
        GetOrdersResponse getOrdersResponse = contractApi.getOrders()
            .xAppNameHeader("invest")
            .xAppVersionHeader("5.0")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetOrdersResponse.class));
        return getOrdersResponse;
    }
}
