package stpTrackingMaster.handleInitializeCommand;


import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaHelper;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static java.time.ZoneOffset.UTC;
import static org.codehaus.groovy.runtime.ScriptBytecodeAdapter.compareTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

@Slf4j
@Epic("handleInitializeCommand - Обработка команд по инициализации виртуального портфеля")
@Feature("TAP-8067")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("Kafka")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class
})
public class HandleInitializeCommandTest {
    KafkaHelper kafkaHelper = new KafkaHelper();

    @Autowired
    BillingService billingService;
    @Autowired
    ProfileService profileService;
    @Autowired
    ClientService clientService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    ContractService contractService;
    @Autowired
    StrategyService strategyService;

    Client client;
    Contract contract;
    Strategy strategy;
    MasterPortfolio masterPortfolio;
    String contractId;
    UUID strategyId;
    String SIEBEL_ID = "5-A0ZYFKHF";


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                strategyService.deleteStrategy(strategy);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contract);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(client);
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
        //находим investId клиента в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWithContractAndStrategy(investId, ClientStatusType.registered, null, contractId, strategyId,
            null, ContractState.untracked, StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем событие для топика kafka tracking.master.command
        long unscaled = 3500000;
        int scale = 1;
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime nowUTC = now.toInstant().atOffset(UTC);
        //создаем команду для топика tracking.master.command
        Tracking.PortfolioCommand command = createCommandToTrackingMasterCommand(now, unscaled, scale);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuff схеме social и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String key = contractId;
        //отправляем событие в топик kafka social.event
        KafkaTemplate<String, byte[]> template = kafkaHelper.createStringToByteTemplate();
        template.setDefaultTopic("tracking.master.command");
        template.sendDefault(key, eventBytes);
        template.flush();
        BigDecimal quantity = new BigDecimal(unscaled * Math.pow(10, -1 * scale));
        //находим запись о портеле мастера в cassandra
        Thread.sleep(3000);
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId);
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
        //находим investId клиента в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        contractId = findValidAccountWithSiebleId.get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWithContractAndStrategy(investId, ClientStatusType.registered, null, contractId, strategyId,
            null, ContractState.untracked, StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
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
        Tracking.PortfolioCommand command = createCommandToTrackingMasterCommand(now, unscaled, scale);
        log.info("Команда в tracking.master.command:  {}", command);
        //кодируем событие по protobuff схеме social и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        String key = contractId;
        //отправляем событие в топик kafka social.event
        KafkaTemplate<String, byte[]> template = kafkaHelper.createStringToByteTemplate();
        template.setDefaultTopic("tracking.master.command");
        template.sendDefault(key, eventBytes);
        template.flush();
        //находим запись о портеле мастера в cassandra
        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId);
        assertThat("версия портеля мастера не равно", masterPortfolio.getVersion(), is(1));
        assertEquals(0, (int) compareTo(masterPortfolio.getBaseMoneyPosition().getQuantity(), baseMoney));
        assertThat("размер positions мастера не равно", masterPortfolio.getPositions().size(), is(0));
        assertThat("дата  портеля мастера не равно", masterPortfolio.getBaseMoneyPosition().getChangedAt(), is(date));
    }

    //***методы для работы тестов**************************************************************************
    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWithContractAndStrategy(UUID investId, ClientStatusType сlientStatusType, SocialProfile socialProfile,
                                             String contractId, UUID strategyId, ContractRole contractRole,
                                             ContractState contractState, StrategyCurrency strategyCurrency,
                                             StrategyRiskProfile strategyRiskProfile, StrategyStatus strategyStatus, LocalDateTime date) {
        client = clientService.createClient(investId, сlientStatusType, socialProfile);
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);

        contract = contractService.saveContract(contract);

        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contract)
            .setTitle("Тест стратегия автотестов")
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription("Тестовая стратегия для работы автотестов")
            .setStatus(strategyStatus)
            .setSlavesCount(0)
            .setActivationTime(date)
            .setScore(1);

        strategy = trackingService.saveStrategy(strategy);
    }

    // создаем команду в топик кафка tracking.master.command
    Tracking.PortfolioCommand createCommandToTrackingMasterCommand(OffsetDateTime time, long unscaled, int scale) {
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractId)
            .setOperation(Tracking.PortfolioCommand.Operation.INITIALIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setPortfolio(Tracking.Portfolio.newBuilder()
                .setVersion(1)
                .setBaseMoneyPosition(Tracking.Portfolio.BaseMoneyPosition.newBuilder()
                    .setQuantity(Tracking.Decimal.newBuilder()
                        .setUnscaled(unscaled)
                        .setScale(scale)
                        .build())
                    .build())
                .build())
            .build();
        return command;
    }
}

