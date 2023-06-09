package stpTrackingSlave.handleActualizeCommand;

import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder2;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrder2Dao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream.PriceUpdatedEvent;
import ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream.PriceUpdatedKey;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.tracking.services.grpc.CapturedResponse;
import ru.qa.tinkoff.tracking.services.grpc.MiddleGrpcService;
import ru.qa.tinkoff.tracking.services.grpc.utils.GrpcServicesAutoConfiguration;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_CONTRACT_EVENT;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_SLAVE_COMMAND;


@Slf4j
@Epic("handleActualizeCommand - Обработка команд на актуализацию")
@Feature("TAP-6864")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@Tags({@Tag("stp-tracking-slave"), @Tag("handleActualizeCommand")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    GrpcServicesAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    StpTrackingSiebelConfiguration.class
})
public class HandleActualizeCommandErrorTest {
    @Autowired
    MiddleGrpcService middleGrpcService;
    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    StringSenderService stringSenderService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    SlavePortfolioDao slavePortfolioDao;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StpTrackingSlaveSteps steps;
    @Autowired
    SlaveOrder2Dao slaveOrder2Dao;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel stpSiebel;
    MasterPortfolio masterPortfolio;
    SlavePortfolio slavePortfolio;
    Client clientSlave;
    Contract contract;
    String contractIdMaster;
    SlaveOrder2 slaveOrder2;
    String contractIdSlave;
    UUID strategyId;
    UUID strategyIdNew;
    String SIEBEL_ID_MASTER;
    String SIEBEL_ID_SLAVE;

    String description = "description test стратегия autotest update adjust base currency";

    @BeforeAll
    void createDataForTests() {
        SIEBEL_ID_MASTER = stpSiebel.siebelIdSlaveMaster;
        SIEBEL_ID_SLAVE = stpSiebel.siebelIdSlaveSlave;
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
                trackingService.deleteStrategy(steps.strategy);
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
                slavePortfolioDao.deleteSlavePortfolio(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
            try {
                slavePortfolioDao.deleteSlavePortfolio(contractIdSlave, strategyIdNew);
            } catch (Exception e) {
            }
            try {
                slaveOrder2Dao.deleteSlaveOrder2(contractIdSlave);
            } catch (Exception e) {
            }
            try {
                steps.createEventInTrackingEvent(contractIdSlave);
            } catch (Exception e) {
            }
        });
    }


    @SneakyThrows
    @Test
    @AllureId("731505")
    @DisplayName("C731505.HandleActualizeCommand.Запись в кэше contractCache по contractId = contract_id не найдена")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C731505() {
        String contractIdSlave = "2054285965";
        UUID strategyId = UUID.randomUUID();
//        //формируем команду на актуализацию для slave
//        //передаем только базовую валюту
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000, contractIdSlave, 2,
            OffsetDateTime.now(), Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        Optional<SlavePortfolio> portfolio = slavePortfolioDao.findLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("731507")
    @DisplayName("C731507.HandleActualizeCommand.Статус договора state != 'tracked' И blocked = false")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C731507() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту master в api сервиса счетов
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
//        //формируем команду на актуализацию для Master
//        //передаем только базовую валюту
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000, contractIdMaster, 2,
            OffsetDateTime.now(), Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdMaster, command);
        //получаем портфель slave
        Optional<SlavePortfolio> portfolio = slavePortfolioDao.findLatestSlavePortfolio(contractIdMaster, strategyId);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("731508")
    @DisplayName("C731508.HandleActualizeCommand.Статус договора state = 'tracked' И blocked = true")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C731508() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        contract = contractService.getContract(contractIdSlave);
        contract.setBlocked(true);
        contractService.saveContract(contract);
        //создаем событие
        OffsetDateTime now = OffsetDateTime.now();
        Tracking.Event event = Tracking.Event.newBuilder()
            .setId(com.google.protobuf.ByteString.copyFromUtf8(UUID.randomUUID().toString()))
            .setAction(Tracking.Event.Action.UPDATED)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(now.toEpochSecond())
                .setNanos(now.getNano())
                .build())
            .setContract(Tracking.Contract.newBuilder()
                .setId(contractIdSlave)
                .setState(Tracking.Contract.State.TRACKED)
                .setBlocked(true)
                .build())
            .build();
        log.info("Команда в tracking.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(TRACKING_CONTRACT_EVENT, contractIdSlave, eventBytes);
//        //формируем команду на актуализацию для slave
//        //передаем только базовую валюту
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000, contractIdSlave, 2,
            OffsetDateTime.now(), Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        Optional<SlavePortfolio> portfolio = slavePortfolioDao.findLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1052282")
    @DisplayName("C1052282.HandleActualizeCommand.Action массива portfolio.position И base_money_position.action не совпадают")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1052282() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //формируем команду на актуализацию для slave  где у позиции и баз. валюты разный action
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 7000, contractIdSlave,
            2, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
                instrument.positionIdAAPL, 2, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            OffsetDateTime.now(), Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        Optional<SlavePortfolio> portfolio = slavePortfolioDao.findLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(31)).stream()
            .filter(key -> key.getKey().equals(contractIdSlave))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении
        checkEventParam(event);
        assertThat("contractId не равен", (event.getContract().getId()), is(contractIdSlave));
        //проверяем запись в базе по договору slave
        contract = contractService.getContract(contractIdSlave);
        assertThat("action события не равен", contract.getBlocked(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1052362")
    @DisplayName("C1052362.HandleActualizeCommand.Стратегия не найдена в strategyCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1052362() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdSlave = resAccountMaster.getInvestId();
        contractIdSlave = resAccountMaster.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        steps.createClientWithContract(investIdSlave, null, contractIdSlave, null, ContractState.tracked, strategyId);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000, contractIdSlave,
            2, OffsetDateTime.now(), Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        Optional<SlavePortfolio> portfolio = slavePortfolioDao.findLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(31));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении
        checkEventParam(event);
        assertThat("contractId не равен", (event.getContract().getId()), is(contractIdSlave));
        //проверяем запись в базе по договору slave
        contract = contractService.getContract(contractIdSlave);
        assertThat("action события не равен", contract.getBlocked(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1052844")
    @DisplayName("C1052844.HandleActualizeCommand.Version из команды <= slave_portfolio.version текущего портфеля")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1052844() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithTwoPos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "5",
            instrument.tickerABBV, instrument.tradingClearingAccountABBV, instrument.positionIdABBV,
            "1", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 4, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySlave = "6551.10";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithTwoPosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "2", true, true,
            instrument.tickerABBV, instrument.tradingClearingAccountABBV, instrument.positionIdABBV, "1", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 4,
            baseMoneySlave, date, createListSlaveOnePos);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        //формируем команду на актуализацию с меньшей версией
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 68556, contractIdSlave,
            2, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,
                5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Проверяем positionId", slavePortfolio.getPositions().get(0).getPositionId(), is(instrument.positionIdAAPL));
        assertThat("Version в портфеле slave не равно", slavePortfolio.getVersion(), is(3));
        assertThat("базовая валюта в портфеле slave не равно", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по портфелю не равно", order.isPresent(), is(false));
    }

    @SneakyThrows
    @Test
    @AllureId("731545")
    @DisplayName("C731545.HandleActualizeCommand.Портфель мастера не найден")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C731545() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        //передаем только базовую валюту
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000, contractIdSlave, versionMiddle,
            OffsetDateTime.now(), Tracking.Portfolio.Action.ADJUST, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        Optional<SlavePortfolio> portfolio = slavePortfolioDao.findLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(31));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении
        checkEventParam(event);
        assertThat("contractId не равен", (event.getContract().getId()), is(contractIdSlave));
    }


    @SneakyThrows
    @Test
    @AllureId("748477")
    @DisplayName("C748477.HandleActualizeCommand.Ошибка при анализе портфеля slave")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C748477() {
        String ticker = "3988";
        String tradingClearingAccount = "L01+00000SPB";
        UUID positionId = UUID.fromString("37bbd1a8-b68e-4fca-b082-4da26528a8b6");
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(ticker, tradingClearingAccount,
            positionId, "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //формируем команду на актуализацию для slave
        //передаем только базовую валюту
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyBaseMoney(0, 7000,
            contractIdSlave, 2, time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель slave
        Optional<SlavePortfolio> portfolio = slavePortfolioDao.findLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(31));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении
        checkEventParam(event);
        assertThat("contractId не равен", (event.getContract().getId()), is(contractIdSlave));
    }


    @SneakyThrows
    @ParameterizedTest
    @NullSource
    @ValueSource(bytes = 2)
    @AllureId("857703")
    @DisplayName("C857703.HandleActualizeCommand.Подтверждение выставленной ранее заявки," +
        "изменилось несколько позиций с position.action IN ('SECURITY_BUY_TRADE', 'SECURITY_SELL_TRADE') в команде")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C857703(Byte state) {
        //String SIEBEL_ID_SLAVE = "5-1HE55RPOV";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        // создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "0", date, 1,
            new BigDecimal("107.79"), new BigDecimal("0"), new BigDecimal("0.0751"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        // создаем запись на выставленную заявку state= null
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 2, 1,
            0, instrument.classCodeAAPL, 3, new BigDecimal("0"), UUID.randomUUID(),
            UUID.randomUUID(),  new BigDecimal("107.79"), new BigDecimal("5"), state, instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, null);
        //создаем команду с несколькими позициями
        OffsetDateTime time = OffsetDateTime.now();
        ru.tinkoff.trading.tracking.Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setScale(1)
            .setUnscaled(58556)
            .build();
        Tracking.Portfolio.Position positionOne = Tracking.Portfolio.Position.newBuilder()
            .setTicker(instrument.tickerAAPL)
            .setTradingClearingAccount(instrument.tradingClearingAccountAAPL)
            .setQuantity(Tracking.Decimal.newBuilder()
                .setUnscaled(5).build())
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        Tracking.Portfolio.Position positionTwo = Tracking.Portfolio.Position.newBuilder()
            .setTicker(instrument.tickerABBV)
            .setTradingClearingAccount(instrument.tradingClearingAccountABBV)
            .setQuantity(Tracking.Decimal.newBuilder()
                .setUnscaled(3).build())
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(3)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setQuantity(quantityBaseMoney)
                    .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                        .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE)
                        .build())
                    .build())
                .addPosition(positionOne)
                .addPosition(positionTwo)
                .setDelayedCorrection(false)
                .build())
            .build();
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на актуализацию
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(10));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении и таб. contract
        checkEventParam(event);
        checkContractParam(contractIdSlave);
    }


    @SneakyThrows
    @Test
    @AllureId("857730")
    @DisplayName("C857730.HandleActualizeCommand. Подтверждение выставленной ранее заявки, " +
        "slave_order.state = null И slave_order.ticker != position.ticker из команды И " +
        "slave_order.trading_clearing_account != position.trading_clearing_account из команды")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C857730() {
        //String SIEBEL_ID_SLAVE = "5-1HE55RPOV";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
        // создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        String baseMoneySl = "7000.0";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "0", date,
            1, new BigDecimal("107.79"), new BigDecimal("0"),
            new BigDecimal("0.0751"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        //делаем запись о выставленной заявке
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 2, 1,
            0, instrument.classCodeAAPL, 3, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("107.79"), new BigDecimal("5"),
            null, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, null);
        //создаем команду с  позицией отличной от той, что в заявке
        OffsetDateTime time = OffsetDateTime.now();
        ru.tinkoff.trading.tracking.Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setScale(1)
            .setUnscaled(58556)
            .build();
        Tracking.Portfolio.Position position = Tracking.Portfolio.Position.newBuilder()
            .setTicker(instrument.tickerABBV)
            .setTradingClearingAccount(instrument.tradingClearingAccountABBV)
            .setQuantity(Tracking.Decimal.newBuilder()
                .setUnscaled(3).build())
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(3)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                        .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE)
                        .build())
                    .build())
                .addPosition(position)
                .setDelayedCorrection(false)
                .build())
            .build();
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuf схеме  tracking.proto и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(TRACKING_SLAVE_COMMAND, contractIdSlave, eventBytes);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(10));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении и таб. contract
        checkEventParam(event);
        checkContractParam(contractIdSlave);
    }


    //необходимо отрицательное значение по EUR, 2-AAPL, 10-USD
    @SneakyThrows
    @Test
    @AllureId("1482080")
    @DisplayName("C1482080.HandleActualizeCommand.Формирование актуального списка позиций из Middle." +
        "Отрицательное значение по позиции [securities|money].balance + [securities|money].blocked < 0")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1482080() {
        String SIEBEL_ID_SLAVE = "5-25U8AFXF";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        UUID investIdSlave = resAccountSlave.getInvestId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description,
            StrategyCurrency.usd, StrategyRiskProfile.aggressive, StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "5", date, 2,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "1", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 3,
            baseMoneySl, date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeOnlyPosition(contractIdSlave, versionMiddle,
            steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, 5,
                Tracking.Portfolio.Action.SECURITY_BUY_TRADE), time, false);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(10));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении и таб. contract
        checkEventParam(event);
        checkContractParam(contractIdSlave);
        //получаем портфель slave
        Optional<SlavePortfolio> portfolio = slavePortfolioDao.findLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
    }


    //д.б. USD=7000, AAPL=2
    @SneakyThrows
    @Test
    @AllureId("1598880")
    @Tags({@Tag("qa")})
    @DisplayName("C1598880.HandleActualizeCommand.Определяем актуальный список позиций в портфеле из Middle" +
        " в событии TRACKING_STATE_UPDATE, вернулась ошибка (параметр error) от Middle")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1598880() {
        String SIEBEL_ID_SLAVE = "5-6JZZQETH";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = "2000115979";
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "5", date, 1,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 7000, contractIdSlave,
            1, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, 2,
                Tracking.Portfolio.Action.TRACKING_STATE_UPDATE), time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        Optional<SlavePortfolio> portfolio = slavePortfolioDao.findLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 1);
        assertThat("запись по портфелю не равно", portfolio.isPresent(), is(false));
    }


    @SneakyThrows
    @ParameterizedTest
    @NullSource
    @ValueSource(bytes = 2)
    @AllureId("1701547")
    @DisplayName("C1701547.HandleActualizeCommand.Определяем, находится ли портфель slave'а в процессе синхронизации." +
        " Проверка на возможность запуска синхронизации. action=TRACKING_STATE_UPDATE." +
        " order_state = null или order.state = 2.Subscription.Blocked = true")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1701547(Byte state) {
        String SIEBEL_ID_SLAVE = "5-6JZZQETH";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, true);
        strategyIdNew = UUID.randomUUID();
        //делаем запись о выставленной заявке по стратегии, на которую slave был подписан ранее, где state = null
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyIdNew, 2, 1,
            0, instrument.classCodeFB, 12, new BigDecimal("2"), UUID.randomUUID(),
            UUID.randomUUID(), new BigDecimal("107.79"), new BigDecimal("2"), state,
            instrument.tickerFB, instrument.tradingClearingAccountFB, instrument.positionIdFB, null);
        //создаем запись по портфелю на прошлую стратегию
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerFB,
            instrument.tradingClearingAccountFB, instrument.positionIdFB, "3", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyIdNew, 2, 12,
            baseMoneySl, date, createListSlaveOnePos);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 7000, contractIdSlave,
            versionMiddle, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
                instrument.positionIdAAPL, 2, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(1)).pollInterval(Duration.ofNanos(200)).until(() ->
            slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("Проверяем positionId", slavePortfolio.getPositions().get(0).getPositionId(), is(instrument.positionIdAAPL));
        await().atMost(Duration.ofSeconds(2)).until(() ->
            slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave), notNullValue());
        assertThat("State не равно", slaveOrder2.getState().toString(), is("0"));
        //проверяем, что новая заявка не выставлена
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по портфелю не равно", order.isPresent(), is(true));
    }


    //д.б. USD=7000, AAPL=2
    @SneakyThrows
    @ParameterizedTest
    @NullSource
    @ValueSource(bytes = 2)
    @AllureId("1701486")
    @DisplayName("C1701486.HandleActualizeCommand.Определяем, находится ли портфель slave'а в процессе синхронизации." +
        " Проверка на возможность запуска синхронизации. action=TRACKING_STATE_UPDATE. order_state = null.Subscription.Blocked = false")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1701486(Byte state) {
        String SIEBEL_ID_SLAVE = "5-6JZZQETH";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "5", date, 1,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlockedContract(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        strategyIdNew = UUID.randomUUID();
        //делаем запись о выставленной заявке по стратегии, на которую slave был подписан ранее, где state = null
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyIdNew, 2, 1,
            0, instrument.classCodeFB, 12, new BigDecimal("2"), UUID.randomUUID(),
            UUID.randomUUID(), new BigDecimal("107.79"), new BigDecimal("2"), state,
            instrument.tickerFB, instrument.tradingClearingAccountFB, instrument.positionIdFB, null);
        //создаем запись по портфелю на прошлую стратегию
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerFB,
            instrument.tradingClearingAccountFB, instrument.positionIdFB,
            "3", date);
        String baseMoneySl = "3000.0";
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyIdNew, 2, 12,
            baseMoneySl, date, createListSlaveOnePos);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(0, 7000, contractIdSlave,
            versionMiddle, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
                instrument.positionIdAAPL, 2, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE),
            time, Tracking.Portfolio.Action.TRACKING_STATE_UPDATE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //checkComparedToMasterVersion(2);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(1)).pollInterval(Duration.ofNanos(200)).until(() ->
            slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId), notNullValue());
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("Проверяем positionId", slavePortfolio.getPositions().get(0).getPositionId(), is(instrument.positionIdAAPL));
        slaveOrder2 = slaveOrder2Dao.getSlaveOrder2(contractIdSlave);
        assertThat("State не равно", slaveOrder2.getState().toString(), is("0"));
        //проверяем, что новая заявка не выставлена
        Optional<SlaveOrder2> order = slaveOrder2Dao.findSlaveOrder2(contractIdSlave);
        assertThat("запись по портфелю не равно", order.isPresent(), is(true));
    }


    @SneakyThrows
    @Test
    @Tags({@Tag("qa")})
    @AllureId("1857060")
    @DisplayName("C1857060. Блокируем контракт, если created_at из команды < slave_portfolio.changed_at")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки изменений позиций договоров, участвующих в автоследовании:" +
        "Version из команды - slave_portfolio.version текущего портфеля = 1, action != 'MORNING_UPDATE'")
    void C1857060() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        // создаем портфель slave с позицией в кассандре
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, "3", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 3, 3,
            "7000.0", date, createListSlaveOnePos);
        OffsetDateTime time = OffsetDateTime.now().minusMinutes(1);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //формируем команду на актуализацию для slave
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(2, 588486,
            contractIdSlave, 4, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
                instrument.positionIdAAPL, 5, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofSeconds(2)).until(() ->
            contract = contractService.getContract(contractIdSlave), notNullValue());
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(5));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении и таб. contract
        checkEventParam(event);
        checkContractParam(contractIdSlave);
    }

    @SneakyThrows
    @Test
    @Tags({@Tag("qa")})
    @AllureId("1856559")
    @DisplayName("C1856559.HandleActualizeCommand.Подтверждение выставленной ранее заявки," +
        " получили подтверждение на большее кол-во актива, чем было выставлено в заявке")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C1856559() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //получаем текущую дату
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = steps.createListMasterPositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, null,
            "5", date, 2, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "6551.10", masterPos);
        //создаем подписку на стратегию для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlocked(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        String baseMoneySl = "7000.0";
        int version = 2;
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePos(instrument.tickerAAPL,
            instrument.tradingClearingAccountAAPL, null, "0", date, 1, new BigDecimal("107.79"),
            new BigDecimal("0"), new BigDecimal("0.076"), new BigDecimal("5"));
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, version, 3,
            baseMoneySl, date, createListSlaveOnePos);
        BigDecimal slavePosQuantityBefore = new BigDecimal("0");
        BigDecimal positionQuantity = new BigDecimal("5");
        //делаем запись о выставленной заявке
        OffsetDateTime createAtLast = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        slaveOrder2Dao.insertIntoSlaveOrder2(contractIdSlave, createAtLast, strategyId, 2, 1,
            0, instrument.classCodeAAPL, 3, new BigDecimal("0"), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("107.79"), positionQuantity,
            (byte) 2, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL, instrument.positionIdAAPL, null);
        //формируем команду на актуализацию для slave
        OffsetDateTime time = OffsetDateTime.now();
        int posQuantityCommand = 6;
        Tracking.PortfolioCommand command = createCommandActualizeWithPosition(1, 58556, contractIdSlave,
            3, steps.createPosInCommand(instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
                instrument.positionIdAAPL, posQuantityCommand, Tracking.Portfolio.Action.SECURITY_BUY_TRADE),
            time, Tracking.Portfolio.Action.SECURITY_BUY_TRADE, false);
        //вычитываем все события из tracking.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        steps.createCommandActualizeTrackingSlaveCommand(contractIdSlave, command);
        //получаем портфель мастера
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractIdMaster, strategyId);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).pollDelay(Duration.ofMillis(600)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, 3), notNullValue());
        assertThat("Время changed_at не равно", slavePortfolio.getChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS),
            is(time.toInstant().truncatedTo(ChronoUnit.SECONDS)));
        assertThat("Проверяем positionId", slavePortfolio.getPositions().get(0).getPositionId(), is(instrument.positionIdAAPL));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(31));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.event:  {}", event);
        //проверяем, данные в сообщении
        checkEventParam(event);
        assertThat("contractId не равен", (event.getContract().getId()), is(contractIdSlave));
        //проверяем запись в базе по договору slave
        contract = contractService.getContract(contractIdSlave);
        assertThat("флаг блокировки для контракта не равен", contract.getBlocked(), is(true));
    }


    // методы для работы тестов*************************************************************************
    // отправляем событие в tracking.test.md.prices.int.stream
    public void createEventTrackingTestMdPricesInStream(String instrumentId, String type, String oldPrice, String newPrice) {
        String event = PriceUpdatedEvent.getKafkaTemplate(LocalDateTime.now(ZoneOffset.UTC), instrumentId, type, oldPrice, newPrice);
        String key = PriceUpdatedKey.getKafkaTemplate(instrumentId);
        //отправляем событие в топик kafka tracking.test.md.prices.int.stream
        stringSenderService.send(Topics.TRACKING_TEST_MD_PRICES_INT_STREAM, key, event);
    }


    public void checkPositionParameters(int pos, String ticker, String tradingClearingAccount, String quantityPos,
                                        BigDecimal price, BigDecimal slavePositionRate,
                                        BigDecimal rateDiff, BigDecimal quantityDiff) {
        assertThat("ticker бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getQuantity().toString(), is(quantityPos));
        assertThat("Price позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getPrice(), is(price));
        assertThat("Rate позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRate(), is(slavePositionRate));
        assertThat("RateDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRateDiff(), is(rateDiff));
        assertThat("QuantityDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getQuantityDiff(), is(quantityDiff));
    }


    //проверяем, парамерты message события в топике tracking.event
    void checkEventParam(Tracking.Event event) {
        assertThat("ID события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("ID договора не равен", event.getContract().getState().toString(), is("TRACKED"));
        assertThat("ID стратегии не равен", (event.getContract().getBlocked()), is(true));
    }

    //проверяем запись по контракту в табл. contract
    void checkContractParam(String contractIdSlave) {
        contract = contractService.getContract(contractIdSlave);
        assertThat("статус клиента не равно", (contract.getState()).toString(), is("tracked"));
        assertThat("статус клиента не равно", (contract.getBlocked()), is(true));
    }


    Tracking.PortfolioCommand createCommandActualizeOnlyBaseMoney(int scale, int unscaled, String contractIdSlave,
                                                                  int version, OffsetDateTime time,
                                                                  Tracking.Portfolio.Action action, boolean delayedCorrection) {
        ru.tinkoff.trading.tracking.Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setScale(scale)
            .setUnscaled(unscaled)
            .build();
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(version)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setQuantity(quantityBaseMoney)
                    .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                        .setAction(action)
                        .build())
                    .build())
                .setDelayedCorrection(delayedCorrection)
                .build())
            .build();
        return command;
    }


    Tracking.PortfolioCommand createCommandActualizeWithPosition(int scale, int unscaled, String contractIdSlave,
                                                                 int version, Tracking.Portfolio.Position position,
                                                                 OffsetDateTime time, Tracking.Portfolio.Action action,
                                                                 boolean delayedCorrection) {
        ru.tinkoff.trading.tracking.Tracking.Decimal quantityBaseMoney = Tracking.Decimal.newBuilder()
            .setScale(scale)
            .setUnscaled(unscaled)
            .build();
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(version)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setQuantity(quantityBaseMoney)
                    .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                        .setAction(action)
                        .build())
                    .build())
                .addPosition(position)
                .setDelayedCorrection(delayedCorrection)
                .build())
            .build();
        return command;
    }

    @Step("Переместить offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(3)), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }


    Tracking.PortfolioCommand createCommandActualizeOnlyPosition(String contractIdSlave, int version,
                                                                 Tracking.Portfolio.Position position,
                                                                 OffsetDateTime time, boolean delayedCorrection) {
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.ACTUALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(version)
                .addPosition(position)
                .setDelayedCorrection(delayedCorrection)
                .build())
            .build();
        return command;
    }

    @Step("Получаем значение по базовой валюте из middle GRPC и преобразовываем полученное значение ")
    BigDecimal getBaseMoneyFromMiof(CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions, String curBaseMoney) {
        //сохраняем данные по позиции валюта
        List<ru.tinkoff.invest.miof.Client.MoneyPosition> listMoney = clientPositions.getResponse().getClientPositions().getMoneyList().stream()
            .filter(ls -> ls.getCurrency().equals(curBaseMoney))
            .filter(ls -> ls.getKind().name().equals("T365"))
            .collect(Collectors.toList());
        //считаем значение BaseMoneyPosition
        double quantityCurrencyMiof = listMoney.get(0).getBalance().getUnscaled()
            * Math.pow(10, -1 * listMoney.get(0).getBalance().getScale())
            + listMoney.get(0).getBlocked().getUnscaled()
            * Math.pow(10, -1 * listMoney.get(0).getBlocked().getScale());
        //переводим в формат BigDecimal полученное значение
        BigDecimal baseMoney = new BigDecimal(quantityCurrencyMiof, MathContext.DECIMAL64);
        return baseMoney;
    }

}
