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
import ru.qa.tinkoff.creator.ApiCreator;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder2;
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
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.ContractApi;
import ru.qa.tinkoff.swagger.tracking.model.GetOrdersResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    SlaveOrder2Dao slaveOrder2Dao;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ApiCreator<ContractApi> contractApiCreator;

    String siebelIdMaster;
    String siebelIdSlave;
    String contractIdSlave;
    String contractIdMaster;
    UUID investIdSlave;
    UUID investIdMaster;
    UUID strategyId;
    UUID idempotencyKey;
    String title;
    String description;
    Integer maxLimit = 100;
    Integer defaultLimit = 30;


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
                slaveOrder2Dao.deleteSlaveOrder2(contractIdSlave);
            } catch (Exception e) {
            }
            try {
                steps.createEventInTrackingContractEvent(contractIdMaster);
            } catch (Exception e) {
            }
            try {
                steps.createEventInTrackingContractEvent(contractIdSlave);
            } catch (Exception e) {
            }
        });
    }


    @BeforeAll
    void getDataClients() {
        siebelIdMaster = stpSiebel.siebelIdApiMaster;
        siebelIdSlave = stpSiebel.siebelIdApiSlave;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(contractIdSlave, investIdSlave);
        steps.deleteDataFromDb(contractIdMaster, investIdMaster);
    }

    @BeforeEach
    void getStrategyData() {
        title = "Autotest" + randomNumber(0, 100);
        description = "Autotest getOrders";
        strategyId = UUID.randomUUID();
    }

    @SneakyThrows
    @Test
    @AllureId("1416878")
    @DisplayName("1416878.getOrders. Получение списка заявок. Успешное получение списка. limit не передан")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1416878() {
        //создаем в БД tracking данные: client, contract, strategy в статусе active
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
        createTestDataSlaveOrder2(1, 16, 0, 1, instrument.classCodeAAPL, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        createTestDataSlaveOrder2(1, 16, 16, 0, instrument.classCodeAAPL, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //вызываем метод getOrders, получаем ответ и проверяем
        GetOrdersResponse getOrdersResponse = getOrders();
        List<SlaveOrder2> getListFromSlaveOrder = slaveOrder2Dao.findSlaveOrder2Limit(contractIdSlave, 30);

        List<ru.qa.tinkoff.swagger.tracking.model.Order> getItemsList = new ArrayList<>();
        List<SlaveOrder2> getItemsListFromDb = new ArrayList<>();

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
            assertThat("quantity не равен " + getItemsList.get(i).getQuantity(), getItemsList.get(i).getQuantity(),
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
    void C1412296(int limit, boolean hasNext) {
        //создаем клиента, контракт и стратегию
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11");
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder2(1, 102, 0, 1, instrument.classCodeAAPL, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //вызываем метод getOrders
        GetOrdersResponse getOrdersResponse = contractApiCreator.get().getOrders()
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
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11");
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,
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
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11");
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder2(2, 1, 1, 1, instrument.classCodeSBERT, instrument.tickerSBERT, instrument.tradingClearingAccountSBERT);
        createTestDataSlaveOrder2(1, 1, 2, 1, instrument.classCodeAAPL, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //вызываем метод getOrders, получаем ответ и проверяем
        GetOrdersResponse getDataOrders = getOrders();
        //для значения курсора используется результат до фильтрации позиций, по которым не были найдены данные, хотя в сам ответ они не попадают
        long nextCursor = getNextCursore(getOrderByAttemptsCount(contractIdSlave, 2));
        //Игнорируем записи с ticker1
        assertThat("items не равно", getDataOrders.getItems().size(), is(1));
        assertThat("hasNext не равен", getDataOrders.getHasNext(), is(false));
        assertThat("cursor не равен", getDataOrders.getNextCursor(), is(String.valueOf(nextCursor)));
    }

    @SneakyThrows
    @Test
    @AllureId("1411778")
    @DisplayName("1411778. getOrders. Получение списка заявок. Slave не подписан на стратегию")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка заявок, выставляемых от лица ведомого в рамках стратегии.")
    void C1411778() {
        //создаем клиента, контракт и стратегию
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11");
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
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11");
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder2(1, 10, 1, 0, instrument.classCodeAAPL, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //Получаем курсор 8'ой записи
        Optional<SlaveOrder2> getSlaveOrderNumberEight = getOrderByAttemptsCount(contractIdSlave, 8);
        String cursoreForOrderEight = String.valueOf(getNextCursore(getSlaveOrderNumberEight));
        //Получаем курсор для крайней записи
        Optional<SlaveOrder2> getFirstSlaveOrder = getOrderByAttemptsCount(contractIdSlave, 2);
        String cursoreForFirstOrder = String.valueOf(getNextCursore(getFirstSlaveOrder));
        //вызываем метод getOrders
        GetOrdersResponse getOrdersResponse = contractApiCreator.get().getOrders()
            .xAppNameHeader("invest")
            .xAppVersionHeader("5.0")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdPath(contractIdSlave)
            .cursorQuery(cursoreForOrderEight)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetOrdersResponse.class));
        //получаем ответ и проверяем
        assertThat("cursor не равен", getOrdersResponse.getNextCursor(), is(cursoreForFirstOrder));
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
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11");
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder2(1, 10, 1, 0, instrument.classCodeAAPL, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //вызываем метод getOrders, получаем ответ и проверяем
        GetOrdersResponse getDataOrders = getOrders();
        Optional<SlaveOrder2> getSlaveOrders = slaveOrder2Dao.getAllSlaveOrder2ByContract(contractIdSlave).stream()
            .filter(v -> v.getVersion().equals(1) && v.getAttemptsCount().equals(2))
            .findFirst();
        long nextCursor = getNextCursore(getSlaveOrders);
        assertThat("cursor не равен", getDataOrders.getNextCursor(), is(String.valueOf(nextCursor)));
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
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11");
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder2(1, 10, 0, 1, instrument.classCodeAFX, instrument.tickerAFX, instrument.tradingClearingAccountAFX);
        createTestDataSlaveOrder2(2, 1, 1, 1, instrument.classCodeAAPL, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //вызываем метод getOrders, получаем ответ и проверяем
        GetOrdersResponse getDataOrders = getOrders();
        String nextCursore = String.valueOf(getNextCursore(getOrderByAttemptsCount(contractIdSlave, 1)));
        assertThat("cursor не равен", getDataOrders.getNextCursor(), is(nextCursore));
        assertThat("items не равен", getDataOrders.getItems().size(), is(1));
        assertThat("ticker не равен", getDataOrders.getItems().get(0).getExchangePosition().getTicker(), is(instrument.tickerAAPL));
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
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11");
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder2(1, 1, 0, 1, instrument.classCodeAAPL, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        createTestDataSlaveOrder2(2, 1, 1, 3, instrument.classCodeAAPL, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //вызываем метод getOrders, получаем ответ и проверяем
        GetOrdersResponse getDataOrders = getOrders();
        String nextCursor = String.valueOf(getNextCursore(getOrderByAttemptsCount(contractIdSlave, 1)));
        assertThat("cursor не равен", getDataOrders.getNextCursor(), is(nextCursor));
        assertThat("items не равен", getDataOrders.getItems().size(), is(1));
        assertThat("ticker не равен", getDataOrders.getItems().get(0).getExchangePosition().getTicker(), is(instrument.tickerAAPL));
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
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster,
            ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04",
            false, new BigDecimal(58.00), "TEST", "TEST11");
        //создаем подписку клиента slave на strategy клиента master
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,
            ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //вставляем запись о заявке в таблицу slave_order
        createTestDataSlaveOrder2(1, 1, 0, 1, instrument.classCodeTCSG, instrument.tickerTCSG, instrument.tradingClearingAccountTCSG);
        createTestDataSlaveOrder2(2, 1, 1, 1, instrument.classCodeTBIO, instrument.tickerTBIO, instrument.tradingClearingAccountTBIO);
        createTestDataSlaveOrder2(3, 1, 2, 1, instrument.classCodeAAPL, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL);
        //вызываем getOrders, получаем ответ и проверяем
        GetOrdersResponse getDataOrders = getOrders();
        String nextCursore = String.valueOf(getNextCursore(getOrderByAttemptsCount(contractIdSlave, 1)));
        assertThat("cursor не равен", getDataOrders.getNextCursor(), is(nextCursore));
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
    void createSlaveOrder2(int minusDays, int minusHours, String contractId, UUID strategyId, int version, Integer attemptsCount,
                           int action, String classCode, BigDecimal filledQuantity,
                           UUID idempotencyKey, BigDecimal price, BigDecimal quantity, Byte state, String ticker, String tradingClearingAccount) {

        OffsetDateTime createAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(minusDays).minusHours(minusHours);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractId, createAt, strategyId, version, attemptsCount,
            action, classCode, 3, filledQuantity, idempotencyKey,
            UUID.randomUUID(), price, quantity, state,
            ticker, tradingClearingAccount);
    }

    //метод создает записи по заявкам в рамках одной стратегии
    @SneakyThrows
    void createTestDataSlaveOrder2(int version, int count, int attemptsCounts, int action, String classCode, String ticker, String tradingClearingAccount) {
        idempotencyKey = UUID.randomUUID();
        for (int i = 0; i < count; i++) {
            attemptsCounts = attemptsCounts + 1;
            createSlaveOrder2(43, 9, contractIdSlave, strategyId, version, attemptsCounts, action, classCode,
                new BigDecimal("0"), idempotencyKey, new BigDecimal("173"), new BigDecimal("1"), (byte) 0, ticker, tradingClearingAccount);
            Thread.sleep(500);
        }
    }

    //метод получает список заявок slave
    public GetOrdersResponse getOrders() {
        GetOrdersResponse getOrdersResponse = contractApiCreator.get().getOrders()
            .xAppNameHeader("invest")
            .xAppVersionHeader("5.0")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetOrdersResponse.class));
        return getOrdersResponse;
    }

    long getNextCursore(Optional<SlaveOrder2> slaveOrder) {
        //C getTime получим  МИЛЛИсекунды, значит нужно умножить на 1000 и получим микросекунду
        return slaveOrder.get().getCreateAt().getTime() * 1000;
    }

    Optional<SlaveOrder2> getOrderByAttemptsCount(String contractId, int attemptsCount) {
        Optional<SlaveOrder2> getSlaveOrder = slaveOrder2Dao.getAllSlaveOrder2ByContract(contractIdSlave).stream()
            .filter(filteredByAttemptsCount -> filteredByAttemptsCount.getAttemptsCount().equals(attemptsCount))
            .findFirst();
        return getSlaveOrder;
    }
}
