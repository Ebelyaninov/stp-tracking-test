package stpTrackingMaster.handleInitializeCommand;


import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.TestsStrategy;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingMasterStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.qa.tinkoff.steps.trackingMasterSteps.StpTrackingMasterSteps;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static java.time.ZoneOffset.UTC;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.codehaus.groovy.runtime.ScriptBytecodeAdapter.compareTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static ru.qa.tinkoff.kafka.Topics.*;

@Slf4j
@Epic("handleInitializeCommand - Обработка команд по инициализации виртуального портфеля")
@Tags({@Tag("stp-tracking-master"), @Tag("handleInitializeCommand")})
@Feature("TAP-8067")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("Kafka")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingMasterStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class
})
public class HandleInitializeCommandTest {
    @Autowired
    ClientService clientService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    ContractService contractService;
    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    StpTrackingMasterSteps steps;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    StrategyService strategyService;


    MasterPortfolio masterPortfolio;
    String contractId;
    UUID strategyId;
    String siebelIdMaster;
    String title;
    String description;
    UUID investId;

    @BeforeAll
    void getdataFromInvestmentAccount() {
        siebelIdMaster = stpSiebel.siebelIdMasterStpTrackingMaster;
        int randomNumber = 0 + (int) (Math.random() * 100);
        title = "Autotest " + String.valueOf(randomNumber);
        description = "new test стратегия autotest";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(siebelIdMaster);
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                strategyService.deleteAllStrategyByContractId(contractId);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractService.getContract(contractId));
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investId));
            } catch (Exception e) {
            }
            try {
                masterPortfolioDao.deleteMasterPortfolio(contractId, strategyId);
            } catch (Exception e) {
            }
        });
    }


    @SneakyThrows
    @Test
    @AllureId("640032")
    @DisplayName("C640032.HandleInitializeCommand.Инициирование виртуального портфеля ведущего")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на первичную инициализацию виртуального портфеля master'а.")
    void C640032() {
        strategyId = UUID.randomUUID();
        //создаем клиента со стратегией в статусе неактивная
        steps.createClientWithContractAndStrategy(investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.draft, 0, null);
        //формируем событие для топика kafka tracking.master.command
        long unscaled = 3500000;
        int scale = 1;
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime nowUTC = now.toInstant().atOffset(UTC);
        //создаем команду для топика tracking.master.command
        Tracking.PortfolioCommand command = steps.createCommandToTrackingMasterCommand(contractId, now, unscaled, scale);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме social и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String key = contractId;
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, key, eventBytes);
        BigDecimal quantity = new BigDecimal(unscaled * Math.pow(10, -1 * scale));
        //находим запись о портеле мастера в cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
        assertThat("версия портеля мастера не равно", masterPortfolio.getVersion(), is(1));
        assertEquals(0, (int) compareTo(masterPortfolio.getBaseMoneyPosition().getQuantity(), quantity));
        assertThat("размер positions мастера не равно", masterPortfolio.getPositions().size(), is(0));
        OffsetDateTime offsetDateTime = masterPortfolio.getBaseMoneyPosition().getChangedAt().toInstant().atOffset(UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        assertThat("дата  портеля мастера не равно", formatter.format(offsetDateTime), is(formatter.format(nowUTC)));
    }

    @SneakyThrows
    @Test
    @AllureId("639963")
    @DisplayName("C639963.HandleInitializeCommand.Инициирование виртуального портфеля ведущего")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на первичную инициализацию виртуального портфеля master'а.")
    void C639963() {
        strategyId = UUID.randomUUID();
        steps.createClientWithContractAndStrategy(investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null);
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //создаем портфель master в cassandra
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        BigDecimal baseMoney = new BigDecimal("34000.0");
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(baseMoney)
            .changedAt(date)
            .build();
        masterPortfolioDao.insertIntoMasterPortfolio(contractId, strategyId, 1, baseMoneyPosition, positionList);
        //формируем событие для топика kafka tracking.master.command
        long unscaled = 3700000;
        int scale = 1;
        OffsetDateTime now = OffsetDateTime.now();
        //создаем команду для топика tracking.master.command
        Tracking.PortfolioCommand command = steps.createCommandToTrackingMasterCommand(contractId, now, unscaled, scale);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме social и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String key = contractId;
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, key, eventBytes);
        //находим запись о портфеле мастера в cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
//        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId);
        assertThat("версия портеля мастера не равно", masterPortfolio.getVersion(), is(1));
        assertEquals(0, (int) compareTo(masterPortfolio.getBaseMoneyPosition().getQuantity(), baseMoney));
        assertThat("размер positions мастера не равно", masterPortfolio.getPositions().size(), is(0));
        assertThat("дата  портеля мастера не равно", masterPortfolio.getBaseMoneyPosition().getChangedAt(), is(date));
    }


    @SneakyThrows
    @Test
    @AllureId("1866380")
    @DisplayName("1866380 handleInitializeCommand. Отправка события на расчет минимальной суммы и стоимости портфеля")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на первичную инициализацию виртуального портфеля master'а.")
    void C1866380() {
        strategyId = UUID.randomUUID();
        steps.createClientWithContractAndStrategy(investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null);
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //формируем событие для топика kafka tracking.master.command
        long unscaled = 3700000;
        int scale = 1;
        OffsetDateTime now = OffsetDateTime.now();
        //создаем команду для топика tracking.master.command
        Tracking.PortfolioCommand command = steps.createCommandToTrackingMasterCommand(contractId, now, unscaled, scale);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме social и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String keyMaster = contractId;
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_ANALYTICS_COMMAND);
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, keyMaster, eventBytes);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_ANALYTICS_COMMAND, Duration.ofSeconds(5));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.AnalyticsCommand analyticsCommand = Tracking.AnalyticsCommand.parseFrom(message.getValue());
        //проверяем полученное событие
        UUID strategyIds = UtilsTest.getGuidFromByteArray(analyticsCommand.getStrategyId().toByteArray());
        assertThat("Стратегия не равна", strategyIds, is(strategyId));
        assertThat("Calculation не равен", analyticsCommand.getCalculation().toString(), is("MASTER_PORTFOLIO_VALUE"));
        assertThat("Operation не равен", analyticsCommand.getOperation().toString(), is("CALCULATE"));
        //находим запись о портеле мастера в cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
        assertThat("версия портеля мастера не равно", masterPortfolio.getVersion(), is(1));
        assertThat("размер positions мастера не равно", masterPortfolio.getPositions().size(), is(0));
        assertThat("дата  портеля мастера не равно", masterPortfolio.getBaseMoneyPosition().getChangedAt(), is(date));
    }


    @SneakyThrows
    @Test
    @AllureId("2092684")
    @DisplayName("C2092684.HandleInitializeCommand. Успешная инициализация новой стратегиями если уже были закрытые стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на первичную инициализацию виртуального портфеля master'а.")
    void C2092684() {
        strategyId = UUID.randomUUID();
        steps.createClientWithContractAndStrategy(investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null);
        int randomNumber = 0 + (int) (Math.random() * 10000);
        String newTitle = "Autotest " + String.valueOf(randomNumber);
        //создаем запись о стратегии клиента
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("result", new BigDecimal("0.2"));
        feeRateProperties.put("management", new BigDecimal("0.04"));
        List<TestsStrategy> testsStrategiesList = new ArrayList<>();
        Strategy strategyClosed = new Strategy()
            .setId(UUID.randomUUID())
            .setContract(steps.contractMaster)
            .setTitle(newTitle)
            .setBaseCurrency(StrategyCurrency.rub)
            .setRiskProfile(ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative)
            .setDescription(description)
            .setStatus(StrategyStatus.closed)
            .setSlavesCount(3)
            .setActivationTime(LocalDateTime.now().minusMonths(1).minusMinutes(10))
            .setCloseTime(LocalDateTime.now().minusMonths(1))
            .setScore(1)
            .setFeeRate(feeRateProperties)
            .setOverloaded(false)
            .setTestsStrategy(testsStrategiesList)
            .setBuyEnabled(true)
            .setSellEnabled(true)
            .setBuyEnabled(true)
            .setSellEnabled(true)
            .setTags(testsStrategiesList);
        strategyService.saveStrategy(strategyClosed);

        int secondRandomNumber = 0 + (int) (Math.random() * 10000);
        String newSecondTitle = "Autotest " + String.valueOf(secondRandomNumber);
        Strategy strategyFrozen = new Strategy()
            .setId(UUID.randomUUID())
            .setContract(steps.contractMaster)
            .setTitle(newSecondTitle)
            .setBaseCurrency(StrategyCurrency.rub)
            .setRiskProfile(ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative)
            .setDescription(description)
            .setStatus(StrategyStatus.frozen)
            .setSlavesCount(3)
            .setActivationTime(LocalDateTime.now().minusMinutes(10))
            .setScore(1)
            .setFeeRate(feeRateProperties)
            .setOverloaded(false)
            .setTestsStrategy(testsStrategiesList)
            .setBuyEnabled(true)
            .setSellEnabled(true)
            .setBuyEnabled(true)
            .setSellEnabled(true)
            .setTags(testsStrategiesList);
        strategyService.saveStrategy(strategyFrozen);
        //создаем портфель master в cassandra
        BigDecimal baseMoney = new BigDecimal("370000.0");
        //формируем событие для топика kafka tracking.master.command
        long unscaled = 3700000;
        int scale = 1;
        OffsetDateTime now = OffsetDateTime.now();
        //создаем команду для топика tracking.master.command
        Tracking.PortfolioCommand command = steps.createCommandToTrackingMasterCommand(contractId, now, unscaled, scale);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuf схеме social и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String key = contractId;
        //отправляем команду в топик kafka tracking.master.command
        kafkaSender.send(TRACKING_MASTER_COMMAND, key, eventBytes);
        //находим запись о портфеле мастера в cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
        assertThat("версия портеля мастера не равно", masterPortfolio.getVersion(), is(1));
        assertThat("portfolio.base_money_position.quantity не равно", masterPortfolio.getBaseMoneyPosition().getQuantity(), is(baseMoney));
        assertThat("contract_id не равен", masterPortfolio.getContractId(), is(contractId));
        assertThat("версия портеля мастера не равно", masterPortfolio.getStrategyId(), is(strategyId));
    }

}


