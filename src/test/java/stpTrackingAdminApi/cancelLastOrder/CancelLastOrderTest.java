package stpTrackingAdminApi.cancelLastOrder;

import com.google.protobuf.InvalidProtocolBufferException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.AdminApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.ContractApiAdminCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.services.SlaveOrder2Dao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.model.ErrorResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.ContractApi;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.enums.ClientRiskProfile;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_CONTRACT_EVENT;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_SLAVE_COMMAND;

@Slf4j
@Epic("getSignals - Отмена заявки застрявшей в синхронизации")
@Feature("ISTAP-6157")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("cancelLastOrder")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    AdminApiCreatorConfiguration.class,
    ApiCreatorConfiguration.class
})
public class CancelLastOrderTest {
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    StpTrackingAdminSteps stpTrackingAdminSteps;
    @Autowired
    SlaveOrder2Dao slaveOrder2Dao;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel siebel;
    @Autowired
    StrategyService strategyService;
    @Autowired
    StpTrackingAdminSteps steps;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    ContractApiAdminCreator contractApiAdminCreator;

    String xApiKey = "x-api-key";
    String key = "tracking";
    String keyRead = "tcrm";
    String contractIdSlave;
    UUID strategyId;
    UUID investIdSlave;
    Client clientSlave;

    @BeforeAll
    void getDataFromAccount() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = stpTrackingAdminSteps.getBrokerAccounts(siebel.siebelIdSlaveAdmin);
        investIdSlave = resAccountMaster.getInvestId();
        contractIdSlave = resAccountMaster.getBrokerAccounts().get(0).getId();
        stpTrackingAdminSteps.deleteDataFromDb(siebel.siebelIdMasterAdmin);
    }


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {

            try {
                contractService.deleteContract(contractService.getContract(contractIdSlave));
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investIdSlave));
            } catch (Exception e) {
            }
            try {
                slaveOrder2Dao.deleteSlaveOrder2(contractIdSlave);
            } catch (Exception e) {
            }
        });
    }

    private static Stream<Arguments> provideStateCorect() {
        return Stream.of(
            Arguments.of("2"),
            Arguments.of((Object) null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideStateCorect")
    @AllureId("2043385")
    @DisplayName("C2043385.CancelLastOrder.Отмена заявки застрявшей в синхронизации")
    @Subfeature("Успешные сценарии")
    @Description("Метод предназначен для отмены заявки застрявшей в синхронизации посредством отправки соответствующей команды.")
    void C2043385(Byte state) throws InvalidProtocolBufferException {
        // находим подходящую стратегию
        strategyId = strategyService.getStrategyByStatus(StrategyStatus.active).get(0).getId();
        //создаем запись по клиенту и контракту
        steps.createClientWithContract(investIdSlave, ClientStatusType.none, ClientRiskProfile.aggressive,
            contractIdSlave, ContractState.tracked, strategyId);
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 3, 1,
            0, instrument.classCodeAAPL, 4, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("107.79"), new BigDecimal("2"),
            state, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, null);
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //вызываем метод cancelLastOrder
        Response responseCancelLastOrder = contractApiAdminCreator.get().cancelLastOrder()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader(key)
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(response -> response);
        //Проверяем смену state у записи в slave_order_2
        assertThat("state записи не равен", slaveOrder2Dao.getSlaveOrder2(contractIdSlave).getState().toString(), is("0"));
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseCancelLastOrder.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseCancelLastOrder.getHeaders().getValue("x-server-time").isEmpty());

        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(10));
        Tracking.PortfolioCommand type = null;
        for (int i = 0; i < messages.size(); i++) {
            Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(messages.get(i).getValue());
            if(portfolioCommand.getOperation().equals(Tracking.PortfolioCommand.Operation.ENABLE_SYNCHRONIZATION)){
                type = portfolioCommand;
            }
        }
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand portfolioCommand = Tracking.PortfolioCommand.parseFrom(message.getValue());
        String key = message.getKey();
        log.info("Команда в tracking.slave.command:  {}", portfolioCommand);
        //Проверяем, данные в сообщении
        checkParam(portfolioCommand, "CANCEL_LAST_ORDER", contractIdSlave);

        // Проверяем отправку события с типом ENABLE_SYNCHRONIZATION в топик  tracking.slave.command
        checkParam(type, "ENABLE_SYNCHRONIZATION", contractIdSlave);
    }


    private static Stream<Arguments> provideStateUnCorect() {
        return Stream.of(
            Arguments.of("0"),
            Arguments.of("1")
        );
    }

    @ParameterizedTest
    @MethodSource("provideStateUnCorect")
    @AllureId("2047650")
    @DisplayName("C2047650.CancelLastOrder.Отмена заявки застрявшей в синхронизации.Портфель сейчас не синхронизируется")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод предназначен для отмены заявки застрявшей в синхронизации посредством отправки соответствующей команды.")
    void C2047650(Byte state) {
        // находим подходящую стратегию
        strategyId = strategyService.getStrategyByStatus(StrategyStatus.active).get(0).getId();
        //создаем запись по клиенту и контракту
        steps.createClientWithContract(investIdSlave, ClientStatusType.none, ClientRiskProfile.aggressive,
            contractIdSlave, ContractState.tracked, strategyId);
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 3, 1,
            0, instrument.classCodeAAPL, 4, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("107.79"), new BigDecimal("2"),
            state, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, null);
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //вызываем метод cancelLastOrder
        Response responseCancelLastOrder = contractApiAdminCreator.get().cancelLastOrder()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader(key)
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        ErrorResponse errorResponse = responseCancelLastOrder.as(ErrorResponse.class);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseCancelLastOrder.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseCancelLastOrder.getHeaders().getValue("x-server-time").isEmpty());
        assertThat("код ошибки не равно", errorResponse.getErrorCode(), is("0344-21-B20"));
        assertThat("Сообщение об ошибке не равно", errorResponse.getErrorMessage(), is("Не найдена заявка в процессе синхронизации"));
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(5));
        assertThat("Отправили команду на отмену заявки", messages.size(), is(0));
    }

    @SneakyThrows
    @Test
    @AllureId("2047694")
    @DisplayName("C2047694.CancelLastOrder.Отмена заявки застрявшей в синхронизации.Запись не найдена в таблице slave_order_2")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод предназначен для отмены заявки застрявшей в синхронизации посредством отправки соответствующей команды.")
    void C2047694() {
        // находим подходящую стратегию
        strategyId = strategyService.getStrategyByStatus(StrategyStatus.active).get(0).getId();
        //создаем запись по клиенту и контракту
        steps.createClientWithContract(investIdSlave, ClientStatusType.none, ClientRiskProfile.aggressive,
            contractIdSlave, ContractState.tracked, strategyId);
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //вызываем метод cancelLastOrder
        Response responseCancelLastOrder = contractApiAdminCreator.get().cancelLastOrder()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader(key)
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        ErrorResponse errorResponse = responseCancelLastOrder.as(ErrorResponse.class);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseCancelLastOrder.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseCancelLastOrder.getHeaders().getValue("x-server-time").isEmpty());
        assertThat("код ошибки не равно", errorResponse.getErrorCode(), is("0344-21-B20"));
        assertThat("Сообщение об ошибке не равно", errorResponse.getErrorMessage(), is("Не найдена заявка в процессе синхронизации"));
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(5));
        assertThat("Отправили команду на отмену заявки", messages.size(), is(0));
    }

    @SneakyThrows
    @Test
    @AllureId("2047699")
    @DisplayName("C2047699.CancelLastOrder.Отмена заявки застрявшей в синхронизации.У договора state = 'untracked'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод предназначен для отмены заявки застрявшей в синхронизации посредством отправки соответствующей команды.")
    void C2047699() {
        //создаем запись по клиенту и контракту
        steps.createClientWithContract(investIdSlave, ClientStatusType.none, ClientRiskProfile.aggressive,
            contractIdSlave, ContractState.untracked, null);
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 3, 1,
            0, instrument.classCodeAAPL, 4, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("107.79"), new BigDecimal("2"),
            (byte) 2, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, null);
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_SLAVE_COMMAND);
        //вызываем метод cancelLastOrder
        Response responseCancelLastOrder = contractApiAdminCreator.get().cancelLastOrder()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader(key)
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        ErrorResponse errorResponse = responseCancelLastOrder.as(ErrorResponse.class);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseCancelLastOrder.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseCancelLastOrder.getHeaders().getValue("x-server-time").isEmpty());
        assertThat("код ошибки не равно", errorResponse.getErrorCode(), is("0344-21-B13"));
        assertThat("Сообщение об ошибке не равно", errorResponse.getErrorMessage(), is("Не найден договор в contract, который подключен к автоследованию"));
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SLAVE_COMMAND, Duration.ofSeconds(3));
        assertThat("Отправили команду на отмену заявки", messages.size(), is(0));
    }

    @SneakyThrows
    @Test
    @AllureId("2047712")
    @DisplayName("C2047712.CancelLastOrder.Отмена заявки застрявшей в синхронизации.Запись по контракту не была найдена")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод предназначен для отмены заявки застрявшей в синхронизации посредством отправки соответствующей команды.")
    void C2047712() {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investIdSlave, ClientStatusType.none, null, ClientRiskProfile.conservative);
        //вызываем метод cancelLastOrder
        Response responseCancelLastOrder = contractApiAdminCreator.get().cancelLastOrder()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader(key)
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        ErrorResponse errorResponse = responseCancelLastOrder.as(ErrorResponse.class);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseCancelLastOrder.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseCancelLastOrder.getHeaders().getValue("x-server-time").isEmpty());
        assertThat("код ошибки не равно", errorResponse.getErrorCode(), is("0344-21-B12"));
        assertThat("Сообщение об ошибке не равно", errorResponse.getErrorMessage(), is("Не найден договор в contract"));
    }


    private static Stream<Arguments> provideStringsForHeadersCancelLastOrder() {
        return Stream.of(
            Arguments.of(null, "tracking_admin"),
            Arguments.of("trading-invest", null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideStringsForHeadersCancelLastOrder")
    @AllureId("2047741")
    @DisplayName("C2047741.CancelLastOrder.Отмена заявки застрявшей в синхронизации.Входные параметры: x-app-name, x-tcs-login")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод предназначен для отмены заявки застрявшей в синхронизации посредством отправки соответствующей команды.")
    void C2047741(String name, String login) {
        //вызываем метод confirmMasterClient
        ContractApi.CancelLastOrderOper cancelLastOrder = contractApiAdminCreator.get().cancelLastOrder()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            cancelLastOrder = cancelLastOrder.xAppNameHeader(name);
        }
        if (login != null) {
            cancelLastOrder = cancelLastOrder.xTcsLoginHeader(login);
        }
        cancelLastOrder.execute(ResponseBodyData::asString);
    }


    @Test
    @AllureId("2047813")
    @DisplayName("C2047813.CancelLastOrder.Отмена заявки застрявшей в синхронизации.Авторизация по api-key, " +
        "передаем неверное значение")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод предназначен для отмены заявки застрявшей в синхронизации посредством отправки соответствующей команды.")
    void C2047813() {
        //Вызываем метод activateStrategy с некоррентным значением api-key
        contractApiAdminCreator.get().cancelLastOrder()
            .reqSpec(r -> r.addHeader(xApiKey, "trackinnng"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response);
    }


    @Test
    @AllureId("2047809")
    @DisplayName("C2047809.CancelLastOrder. Авторизация по api-key, передаем значение с доступом read")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод предназначен для отмены заявки застрявшей в синхронизации посредством отправки соответствующей команды.")
    void C2047809() {
        //Вызываем метод activateStrategy с некоррентным значением api-key
        contractApiAdminCreator.get().cancelLastOrder()
            .reqSpec(r -> r.addHeader(xApiKey, keyRead))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response);
    }


    @Test
    @AllureId("2043386")
    @DisplayName("C2043386.CancelLastOrder.Отмена заявки застрявшей в синхронизации, не передаем значение")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод предназначен для отмены заявки застрявшей в синхронизации посредством отправки соответствующей команды..")
    void C2043386() {
        //Вызываем метод activateStrategy без api-key
        contractApiAdminCreator.get().cancelLastOrder()
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
    }


    //Проверяем параметры события
    void checkParam(Tracking.PortfolioCommand command, String operation, String ContractIdSlave) {
        assertThat("Operation события не равен", command.getOperation().toString(), is(operation));
        assertThat("ID договора не равен", (command.getContractId()), is(ContractIdSlave));
    }

}
