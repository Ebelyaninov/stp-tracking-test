package stpTrackingSlave.handleUnblockContractCommand;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrderDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_CONTRACT_EVENT;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_DELAY_COMMAND;
import static ru.qa.tinkoff.matchers.ContractIsNotBlockedMatcher.contractIsNotBlockedMatcher;

@Slf4j
@Epic("handleUnblockContractCommand -Операция для обработки команд, направленных на снятие технической блокировки с договора.")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    GrpcServicesAutoConfiguration.class,
})
public class HandleUnblockContractCommandTest {
    @Autowired
    BillingService billingService;
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
    SlaveOrderDao slaveOrderDao;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ExchangePositionService exchangePositionService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StpTrackingSlaveSteps steps;

    SlavePortfolio slavePortfolio;

    Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    Contract contract;
    UUID strategyId;
    String SIEBEL_ID_MASTER = "5-4LCY1YEB";
    String SIEBEL_ID_SLAVE = "5-JEF71TBN";

    final String tickerApple = "AAPL";
    final String tradingClearingAccountApple = "TKCBM_TCAB";

    final String tickerFB = "FB";
    final String tradingClearingAccountFB = "TKCBM_TCAB";

    String tickerABBV = "ABBV";
    String tradingClearingAccountABBV = "TKCBM_TCAB";

    String description = "description test стратегия autotest update adjust base currency";


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
                slaveOrderDao.deleteSlaveOrder(contractIdSlave, strategyId);
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
    @AllureId("1459261")
    @DisplayName("C1459261.HandleUnblockContractCommand.Version из ответа от gRPC = slave_portfolio.version")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на снятие технической блокировки с договора.")
    void C1459261() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7000", positionListMaster);
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(tickerApple, tradingClearingAccountApple,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6461.9", masterPosOne);
        //создаем подписку для slave c заблокированной подпиской
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlockedContract(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle, 2,
            baseMoneySlave, date, positionList);
        //Вычитываем из топика kafka: tracking.master.command все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //вычитываем из топика кафка tracking.delay.command все offset
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandUnBlockContractSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(Duration.ofSeconds(3))
            .until(() -> contract = contractService.getContract(contractIdSlave), contractIsNotBlockedMatcher());
        assertThat("Версия портфеля slave не равна", contract.getBlocked(), is(false));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(5));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.contract.event:  {}", event);
        //смотрим, сообщение, которое поймали в топике kafka tracking.delay.command
        List<Pair<String, byte[]>> messagesDelay = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> messageDelay = messagesDelay.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(messageDelay.getValue());
        log.info("Событие  в tracking.contract.event:  {}", commandKafka);
        //проверяем, данные в сообщении tracking.contract.event
        checkTrackingContractEvent(event, "UPDATED", contractIdSlave, Tracking.Contract.State.TRACKED, false);
        //проверяем, данные в сообщении tracking.delay.command
        checkTrackingDelayCommand(commandKafka, contractIdSlave, "RETRY_SYNCHRONIZATION");
        assertThat("Время в событии не равно", event.getCreatedAt().getSeconds(), is(commandKafka.getCreatedAt().getSeconds()));
        assertThat("Время в событии не равно", event.getCreatedAt().getNanos(), is(commandKafka.getCreatedAt().getNanos()));
    }


    @SneakyThrows
    @Test
    @AllureId("1459465")
    @DisplayName("C1459465.HandleUnblockContractCommand.Version из ответа от gRPC < slave_portfolio.version")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на снятие технической блокировки с договора.")
    void C1459465() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7000", positionListMaster);
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(tickerApple, tradingClearingAccountApple,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6461.9", masterPosOne);
        //создаем подписку для slave c заблокированной подпиской
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlockedContract(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle + 1, 2,
            baseMoneySlave, date, positionList);
        //отправляем команду на синхронизацию
        steps.createCommandUnBlockContractSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            contract = contractService.getContract(contractIdSlave), notNullValue());
        assertThat("Версия портфеля slave не равна", contract.getBlocked(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1459317")
    @DisplayName("C1459317.HandleUnblockContractCommand.У договора contract_id state != 'tracked'")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на снятие технической блокировки с договора.")
    void C1459317() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7000", positionListMaster);
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(tickerApple, tradingClearingAccountApple,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6461.9", masterPosOne);
        //создаем подписку для slave c заблокированной подпиской
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionDraftWithBlockedContract(investIdSlave, null, contractIdSlave, null, ContractState.untracked,
            strategyId, SubscriptionStatus.draft, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //отправляем команду на синхронизацию
        steps.createCommandUnBlockContractSlaveCommand(contractIdSlave);
        //получаем портфель slave
        Thread.sleep(3000);
        await().atMost(FIVE_SECONDS).until(() ->
            contract = contractService.getContract(contractIdSlave), notNullValue());
        assertThat("Версия портфеля slave не равна", contract.getBlocked(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1459394")
    @DisplayName("C1459394.HandleUnblockContractCommand.У договора contract_id state = 'tracked' И blocked = false")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на снятие технической блокировки с договора.")
    void C1459394() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7000", positionListMaster);
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(tickerApple, tradingClearingAccountApple,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6461.9", masterPosOne);
        //создаем подписку для slave c заблокированной подпиской
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, contractIdSlave, null, ContractState.tracked,
            null, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()), null, false);
        //отправляем команду на синхронизацию
        steps.createCommandUnBlockContractSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            contract = contractService.getContract(contractIdSlave), notNullValue());
        assertThat("Версия портфеля slave не равна", contract.getBlocked(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1469439")
    @DisplayName("C1469439.HandleUnblockContractCommand.Version gRPC > slave_portfolio.version." +
        "Limits_loading_in_progress = false.Запись в таблице master_portfolio не найдена")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на снятие технической блокировки с договора.")
    void C1469439() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
//        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем подписку для slave c заблокированной подпиской
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlockedContract(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 2,
            baseMoneySlave, date, positionList);
        //отправляем команду на синхронизацию
        steps.createCommandUnBlockContractSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            contract = contractService.getContract(contractIdSlave), notNullValue());
        assertThat("Версия портфеля slave не равна", contract.getBlocked(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1459263")
    @DisplayName("C1459263.HandleUnblockContractCommand.У договора contract_id state != 'tracked'")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на снятие технической блокировки с договора.")
    void C1459263() {
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //отправляем команду на синхронизацию
        steps.createCommandUnBlockContractSlaveCommand(contractIdSlave);
        //получаем портфель slave
        Optional<Contract> contractOpt = contractService.findContract(contractIdSlave);
        assertThat("запись по контракту не равно", contractOpt.isPresent(), is(false));
    }


    @SneakyThrows
    @Test
    @AllureId("1463067")
    @DisplayName("C1463067.HandleUnblockContractCommand.Version от gRPC > slave_portfolio.version." +
        "limits_loading_in_progress = false. Не найдена стратегия")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на снятие технической блокировки с договора.")
    void C1463067() {
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();//
        //создаем договор для slave с блокировкой
        steps.createBlockedContract(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 1, 2,
            baseMoneySlave, date, positionList);
        //отправляем команду на синхронизацию
        steps.createCommandUnBlockContractSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(FIVE_SECONDS).until(() ->
            contract = contractService.getContract(contractIdSlave), notNullValue());
        assertThat("Версия портфеля slave не равна", contract.getBlocked(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1459411")
    @DisplayName("C1459411.HandleUnblockContractCommand.Bернулась ошибка (параметр error) при вызове gRPC Middle")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на снятие технической блокировки с договора.")
    void C1459411() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = "2011514582";
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7000", positionListMaster);
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(tickerApple, tradingClearingAccountApple,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6461.9", masterPosOne);
        //создаем подписку для slave c заблокированной подпиской
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlockedContract(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        //создаем портфель для ведомого
        String baseMoneySlave = "6576.23";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, 1, 2,
            baseMoneySlave, date, positionList);
        //отправляем команду на синхронизацию
        steps.createCommandUnBlockContractSlaveCommand(contractIdSlave);
        //получаем портфель slave
        Thread.sleep(3000);
        await().atMost(FIVE_SECONDS).until(() ->
            contract = contractService.getContract(contractIdSlave), notNullValue());
        assertThat("Версия портфеля slave не равна", contract.getBlocked(), is(true));
    }


    @SneakyThrows
    @Test
    @AllureId("1463220")
    @DisplayName("C1463220.HandleUnblockContractCommand.Version gRPC > slave_portfolio.version." +
        "Limits_loading_in_progress = false.Актуальный список позиций из middle.Пустой портфель")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на снятие технической блокировки с договора.")
    void C1463220() {
        String SIEBEL_ID_SLAVE = "5-167ET5VFO";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7000", positionListMaster);
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(tickerApple, tradingClearingAccountApple,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6461.9", masterPosOne);
        //создаем подписку для slave c заблокированной подпиской
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlockedContract(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //создаем портфель для ведомого
        String baseMoneySlave = "0";
        List<SlavePortfolio.Position> positionList = new ArrayList<>();
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 2,
            baseMoneySlave, date, positionList);
        //Вычитываем из топика kafka: tracking.master.command все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на синхронизацию
        steps.createCommandUnBlockContractSlaveCommand(contractIdSlave);
        //получаем портфель slave
//       Thread.sleep(3000);
        await().atMost(Duration.ofSeconds(4))
            .until(() -> contract = contractService.getContract(contractIdSlave),
                contractIsNotBlockedMatcher());
        assertThat("Версия портфеля slave не равна", contract.getBlocked(), is(false));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(5));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        //проверяем, данные в сообщении
        checkTrackingContractEvent(event, "UPDATED", contractIdSlave, Tracking.Contract.State.TRACKED, false);
        await().atMost(Duration.ofSeconds(3)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        List<SlavePortfolio.Position> position = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerApple))
            .collect(Collectors.toList());
        //проверяем значение по базовой валюте
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is(baseMoneySlave));
        // проверяем значение по позиции
        checkPositionParam(position, tickerApple, tradingClearingAccountApple, "0");
    }


    //    в securities 5 позиций по AAPL и 1 по FB
//    в money по USD-1000
    @SneakyThrows
    @Test
    @AllureId("1467169")
    @DisplayName("C1467169.HandleUnblockContractCommand.Version gRPC > slave_portfolio.version." +
        "Limits_loading_in_progress = false.Актуальный список позиций из middle.Позиция есть в мидл но нет в портфеле")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на снятие технической блокировки с договора.")
    void C1467169() {
        String SIEBEL_ID_SLAVE = "4-1XAF19DB";
//        String SIEBEL_ID_SLAVE = "5-2MR943679";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//        UUID investIdSlave = resAccountSlave.getInvestId();
//        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        UUID investIdSlave = UUID.fromString("cfc614af-67bb-49ee-a147-4bdc60777b04");
        contractIdSlave = "2000002457";
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7000", positionListMaster);
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(tickerApple, tradingClearingAccountApple,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6461.9", masterPosOne);
        List<MasterPortfolio.Position> masterPosTwo = steps.createListMasterPositionWithTwoPos(tickerApple, tradingClearingAccountApple,
            "5", tickerFB, tradingClearingAccountFB, "1", date, 3,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "1461.9", masterPosTwo);
        //создаем подписку для slave c заблокированной подпиской
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlockedContract(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //создаем портфель для ведомого
        String baseMoneySlave = "1221";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(tickerApple, tradingClearingAccountApple,
            "5", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //вычитываем из топика кафка tracking.delay.command все offset
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandUnBlockContractSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(Duration.ofSeconds(3))
            .until(() -> contract = contractService.getContract(contractIdSlave),
                contractIsNotBlockedMatcher());
        assertThat("Версия портфеля slave не равна", contract.getBlocked(), is(false));
        //смотрим, сообщение, которое поймали в топике kafka tracking.delay.command
        List<Pair<String, byte[]>> messagesDelay = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> messageDelay = messagesDelay.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(messageDelay.getValue());
        log.info("Событие  в tracking.contract.event:  {}", commandKafka);
        //проверяем message топика kafka tracking.delay.command
        checkTrackingDelayCommand(commandKafka, contractIdSlave, "RETRY_SYNCHRONIZATION");
        //находим новую запись по slave портфелю
        await().atMost(Duration.ofSeconds(3)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerApple))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionFB = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerFB))
            .collect(Collectors.toList());
        //проверяем значение по базовой валюте
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("1000"));
        // проверяем значение по позиции
        checkPositionParam(positionAAPL, tickerApple, tradingClearingAccountApple, "4");
        checkPositionParam(positionFB, tickerFB, tradingClearingAccountFB, "1");
    }


    //    в securities 5 позиций по AAPL и 1 по FB
//    в money по USD-1000
    @SneakyThrows
    @Test
    @AllureId("1467369")
    @DisplayName("C1467369.HandleUnblockContractCommand.Version gRPC > slave_portfolio.version." +
        "Limits_loading_in_progress = false.Актуальный список позиций из middle.Не найден портфель")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на снятие технической блокировки с договора.")
    void C1467369() {
//        String SIEBEL_ID_SLAVE = "5-2MR943679";
        String SIEBEL_ID_SLAVE = "4-1XAF19DB";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//        UUID investIdSlave = resAccountSlave.getInvestId();
//        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        UUID investIdSlave = UUID.fromString("cfc614af-67bb-49ee-a147-4bdc60777b04");
        contractIdSlave = "2000002457";

        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7000", positionListMaster);
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(tickerApple, tradingClearingAccountApple,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6461.9", masterPosOne);
        List<MasterPortfolio.Position> masterPosTwo = steps.createListMasterPositionWithTwoPos(tickerApple, tradingClearingAccountApple,
            "5", tickerFB, tradingClearingAccountFB, "1", date, 3,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "1461.9", masterPosTwo);
        //создаем подписку для slave c заблокированной подпиской
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlockedContract(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //Вычитываем из топика kafka: tracking.master.command все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на синхронизацию
        steps.createCommandUnBlockContractSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(Duration.ofSeconds(3))
            .until(() -> contract = contractService.getContract(contractIdSlave), contractIsNotBlockedMatcher());
        assertThat("Версия портфеля slave не равна", contract.getBlocked(), is(false));
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(5));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        //проверяем, данные в сообщении
        checkTrackingContractEvent(event, "UPDATED", contractIdSlave, Tracking.Contract.State.TRACKED, false);
        //находим новую запись по slave портфелю
        await().atMost(Duration.ofSeconds(3)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerApple))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionFB = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerFB))
            .collect(Collectors.toList());
        //проверяем значение по базовой валюте
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("1107.41"));
        // проверяем значение по позиции
        checkPositionParam(positionAAPL, tickerApple, tradingClearingAccountApple, "4");
        checkPositionParam(positionFB, tickerFB, tradingClearingAccountFB, "1");
    }


    @SneakyThrows
    @Test
    @AllureId("1468523")
    @DisplayName("C1468523.HandleUnblockContractCommand.Version gRPC > slave_portfolio.version." +
        "Limits_loading_in_progress = false.Актуальный список позиций из middle.Фильтруем нулевую позицию")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на снятие технической блокировки с договора.")
    void C1468523() {
//        String SIEBEL_ID_SLAVE = "5-2MR943679";
        String SIEBEL_ID_SLAVE = "5-CV1HRESL";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//        UUID investIdSlave = resAccountSlave.getInvestId();
//        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        UUID investIdSlave = UUID.fromString("cfc614af-67bb-49ee-a147-4bdc60777b04");
        contractIdSlave = "2000002457";
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7000", positionListMaster);
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(tickerApple, tradingClearingAccountApple,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6461.9", masterPosOne);
        //создаем подписку для slave c заблокированной подпиской
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlockedContract(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //создаем портфель для ведомого
        String baseMoneySlave = "6221";
        List<SlavePortfolio.Position> createListSlaveOnePos = steps.createListSlavePositionWithOnePosLight(tickerApple, tradingClearingAccountApple,
            "5", date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 2,
            baseMoneySlave, date, createListSlaveOnePos);
        //вычитываем из топика кафка tracking.delay.command все offset
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //отправляем команду на синхронизацию
        steps.createCommandUnBlockContractSlaveCommand(contractIdSlave);
        //получаем портфель slave
        await().atMost(Duration.ofSeconds(3))
            .until(() -> contract = contractService.getContract(contractIdSlave),
                contractIsNotBlockedMatcher());
        assertThat("Версия портфеля slave не равна", contract.getBlocked(), is(false));
        //смотрим, сообщение, которое поймали в топике kafka tracking.delay.command
        List<Pair<String, byte[]>> messagesDelay = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(20));
        Pair<String, byte[]> messageDelay = messagesDelay.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand commandKafka = Tracking.PortfolioCommand.parseFrom(messageDelay.getValue());
        log.info("Событие  в tracking.contract.event:  {}", commandKafka);
        //проверяем message топика kafka tracking.delay.command
        checkTrackingDelayCommand(commandKafka, contractIdSlave, "RETRY_SYNCHRONIZATION");
        //находим новую запись по slave портфелю
        await().atMost(Duration.ofSeconds(3)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerApple))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionFB = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerFB))
            .collect(Collectors.toList());
        //проверяем значение по базовой валюте
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().toString(), is("1107.41"));
        assertThat("Количество позиций портфеля slave не равна", slavePortfolio.getPositions().size(), is(2));
        // проверяем значение по позиции
        checkPositionParam(positionAAPL, tickerApple, tradingClearingAccountApple, "4");
        checkPositionParam(positionFB, tickerFB, tradingClearingAccountFB, "1");
    }


    @SneakyThrows
    @Test
    @AllureId("1466101")
    @DisplayName("C1466101.HandleUnblockContractCommand.Version gRPC > slave_portfolio.version." +
        "Limits_loading_in_progress = false.Актуальный список позиций из middle.Базовая валюта изменилась")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на снятие технической блокировки с договора.")
    void C1466101() {
//        String SIEBEL_ID_SLAVE = "5-2MR943679";
        String SIEBEL_ID_SLAVE = "4-1XAF19DB";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//        UUID investIdSlave = resAccountSlave.getInvestId();
//        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
//        String clientCodeSlave = resAccountSlave.getBrokerAccounts().get(0).getClientCodes().get(0).getId();
        UUID investIdSlave = UUID.fromString("cfc614af-67bb-49ee-a147-4bdc60777b04");
        contractIdSlave = "2000002457";
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7000", positionListMaster);
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(tickerApple, tradingClearingAccountApple,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6461.9", masterPosOne);
        List<MasterPortfolio.Position> masterPosTwo = steps.createListMasterPositionWithTwoPos(tickerApple, tradingClearingAccountApple,
            "5", tickerFB, tradingClearingAccountFB, "1", date, 3,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "1461.9", masterPosTwo);
        //создаем подписку для slave c заблокированной подпиской
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlockedContract(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //сохраняем данные по бумаге из ответа miof в список
        List<ru.tinkoff.invest.miof.Client.MoneyPosition> listMoney = clientPositions.getResponse().getClientPositions().getMoneyList().stream()
            .filter(ls -> ls.getCurrency().equals("USD"))
            .filter(ls -> ls.getKind().name().equals("T365"))
            .collect(Collectors.toList());
        //расчитываем значение количество бумаг
        double quantityMiof = listMoney.get(0).getBalance().getUnscaled()
            * Math.pow(10, -1 * listMoney.get(0).getBalance().getScale());
        //создаем портфель для ведомого
        String baseMoneySlave = "656.23";
//        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerApple, tradingClearingAccountApple,
//            "5", tickerFB, tradingClearingAccountFB, "1", date);
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerApple, tradingClearingAccountApple,
            "5", true, true, tickerABBV, tradingClearingAccountABBV, "1", true, true, date);
                steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 3,
            baseMoneySlave, date, createListSlavePos);
        //Вычитываем из топика kafka: tracking.master.command все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на синхронизацию
        steps.createCommandUnBlockContractSlaveCommand(contractIdSlave);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.contract.event:  {}", event);
        //получаем портфель slave
        await().atMost(Duration.ofSeconds(3))
            .until(() -> contract = contractService.getContract(contractIdSlave),
                contractIsNotBlockedMatcher());
        assertThat("Версия портфеля slave не равна", contract.getBlocked(), is(false));
        //проверяем, данные в сообщении
        checkTrackingContractEvent(event, "UPDATED", contractIdSlave, Tracking.Contract.State.TRACKED, false);
        //находим новую запись по slave портфелю
        await().atMost(Duration.ofSeconds(3)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerApple))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionFB = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerFB))
            .collect(Collectors.toList());
        //проверяем значение по базовой валюте
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().doubleValue(), is(quantityMiof));
        // проверяем значение по позиции
        checkPositionParam(positionAAPL, tickerApple, tradingClearingAccountApple, "4");
        checkPositionParam(positionFB, tickerFB, tradingClearingAccountFB, "1");
    }


    @SneakyThrows
    @Test
    @AllureId("1467375")
    @DisplayName("C1467375.HandleUnblockContractCommand.Version gRPC > slave_portfolio.version." +
        "Limits_loading_in_progress = false.Актуальный список позиций из middle.Нашли позицию в портфеле и нет в Middle")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на снятие технической блокировки с договора.")
    void C1467375() {
//        String SIEBEL_ID_SLAVE = "5-2MR943679";
        String SIEBEL_ID_SLAVE = "4-1XAF19DB";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
//        UUID investIdSlave = resAccountSlave.getInvestId();
//        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        UUID investIdSlave = UUID.fromString("cfc614af-67bb-49ee-a147-4bdc60777b04");
        contractIdSlave = "2000002457";
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        steps.createMasterPortfolio(contractIdMaster, strategyId, 1, "7000", positionListMaster);
        List<MasterPortfolio.Position> masterPosOne = steps.createListMasterPositionWithOnePos(tickerApple, tradingClearingAccountApple,
            "5", date, 1, steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 2, "6461.9", masterPosOne);
        List<MasterPortfolio.Position> masterPosTwo = steps.createListMasterPositionWithTwoPos(tickerApple, tradingClearingAccountApple,
            "5", tickerFB, tradingClearingAccountFB, "1", date, 3,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        steps.createMasterPortfolio(contractIdMaster, strategyId, 3, "1461.9", masterPosTwo);
        //создаем подписку для slave c заблокированной подпиской
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcriptionWithBlockedContract(investIdSlave, null, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new java.sql.Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false);
        //вызываем метод middle getClientPosition по GRPC, который возвращает список позиций клиента и версию портфеля
        ru.tinkoff.invest.miof.Client.GetClientPositionsReq clientPositionsReq = ru.tinkoff.invest.miof.Client.GetClientPositionsReq.newBuilder()
            .setAgreementId(contractIdSlave)
            .build();
        CapturedResponse<ru.tinkoff.invest.miof.Client.GetClientPositionsResp> clientPositions =
            middleGrpcService.getClientPositions(clientPositionsReq);
        int versionMiddle = clientPositions.getResponse().getClientPositions().getVersion().getValue();
        //сохраняем данные по бумаге из ответа miof в список
        List<ru.tinkoff.invest.miof.Client.MoneyPosition> listMoney = clientPositions.getResponse().getClientPositions().getMoneyList().stream()
            .filter(ls -> ls.getCurrency().equals("USD"))
            .filter(ls -> ls.getKind().name().equals("T365"))
            .collect(Collectors.toList());
        //расчитываем значение количество бумаг
        double quantityMiof = listMoney.get(0).getBalance().getUnscaled()
            * Math.pow(10, -1 * listMoney.get(0).getBalance().getScale());
        //создаем портфель для ведомого
        String baseMoneySlave = "656.23";
//        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerApple, tradingClearingAccountApple,
//            "5", tickerABBV, tradingClearingAccountABBV, "3", date);
        List<SlavePortfolio.Position> createListSlavePos = steps.createListSlavePositionWithTwoPosLight(tickerApple, tradingClearingAccountApple,
            "5", true, true, tickerABBV, tradingClearingAccountABBV, "3", true, true, date);
        steps.createSlavePortfolioWithPosition(contractIdSlave, strategyId, versionMiddle - 2, 3,
            baseMoneySlave, date, createListSlavePos);
        //Вычитываем из топика kafka: tracking.master.command все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на синхронизацию
        steps.createCommandUnBlockContractSlaveCommand(contractIdSlave);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Событие  в tracking.contract.event:  {}", event);
        //получаем портфель slave
        await().atMost(Duration.ofSeconds(3))
            .until(() -> contract = contractService.getContract(contractIdSlave),
                contractIsNotBlockedMatcher());
        assertThat("Версия портфеля slave не равна", contract.getBlocked(), is(false));
        //проверяем, данные в сообщении
        checkTrackingContractEvent(event, "UPDATED", contractIdSlave, Tracking.Contract.State.TRACKED, false);
        //находим новую запись по slave портфелю
        await().atMost(Duration.ofSeconds(3)).until(() ->
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolioWithVersion(contractIdSlave, strategyId, versionMiddle), notNullValue());
        List<SlavePortfolio.Position> positionAAPL = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerApple))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionFB = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerFB))
            .collect(Collectors.toList());
        List<SlavePortfolio.Position> positionABBV = slavePortfolio.getPositions().stream()
            .filter(ps -> ps.getTicker().equals(tickerABBV))
            .collect(Collectors.toList());
        //проверяем значение по базовой валюте
        assertThat("Quantity базовой валюты портфеля slave не равна", slavePortfolio.getBaseMoneyPosition().getQuantity().doubleValue(), is(quantityMiof));
        // проверяем значение по позиции
        checkPositionParam(positionAAPL, tickerApple, tradingClearingAccountApple, "4");
        checkPositionParam(positionFB, tickerFB, tradingClearingAccountFB, "1");
        checkPositionParam(positionABBV, tickerABBV, tradingClearingAccountABBV, "0");
    }


//общие методы для тестов

    //проверяем, данные в сообщении топика tracking.contract.event
    void checkTrackingContractEvent(Tracking.Event event, String action, String contractId,
                                    Tracking.Contract.State state, Boolean blocked) {
        assertThat("action события не равен", event.getAction().toString(), is(action));
        assertThat("contractId не равен", (event.getContract().getId()), is(contractId));
        assertThat("статус договора не равен", (event.getContract().getState()), is(state));
        assertThat("blocked договора не равен", (event.getContract().getBlocked()), is(blocked));
    }

    //проверяем message топика kafka tracking.delay.command
    void checkTrackingDelayCommand(Tracking.PortfolioCommand commandKafka, String contractId, String operation) {
        assertThat("ID договора не равен", commandKafka.getContractId(), is(contractId));
        assertThat("Торгово-клиринговый счет не равен", commandKafka.getOperation().toString(), is(operation));

    }

    //проверяем ппраметры позиции
    void checkPositionParam(List<SlavePortfolio.Position> position, String ticker, String tradingClearingAccount, String quantity) {
        assertThat("ticker бумаги позиции в портфеле slave не равна", position.get(0).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", position.get(0).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", position.get(0).getQuantity().toString(), is(quantity));
    }


}
