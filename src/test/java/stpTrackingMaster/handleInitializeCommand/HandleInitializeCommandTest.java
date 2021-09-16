package stpTrackingMaster.handleInitializeCommand;


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
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.SptTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingMasterStepsConfiguration;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.qa.tinkoff.steps.trackingMasterSteps.StpTrackingMasterSteps;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static java.time.ZoneOffset.UTC;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.codehaus.groovy.runtime.ScriptBytecodeAdapter.compareTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_MASTER_COMMAND;

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
    InvestTrackingAutoConfiguration.class,
    StpTrackingMasterStepsConfiguration.class
})
public class HandleInitializeCommandTest {

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
    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    StpTrackingMasterSteps steps;

    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();


    MasterPortfolio masterPortfolio;
    String contractId;
    UUID strategyId;
    String SIEBEL_ID = "5-2G2O9XVOR";


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
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
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest " +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        steps.createClientWithContractAndStrategy(investId, contractId, null, ContractState.untracked,
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
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest " +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.createClientWithContractAndStrategy(investId, contractId, null, ContractState.untracked,
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
        //находим запись о портеле мастера в cassandra
        await().atMost(FIVE_SECONDS).until(() ->
            masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId), notNullValue());
//        masterPortfolio = masterPortfolioDao.getLatestMasterPortfolio(contractId, strategyId);
        assertThat("версия портеля мастера не равно", masterPortfolio.getVersion(), is(1));
        assertEquals(0, (int) compareTo(masterPortfolio.getBaseMoneyPosition().getQuantity(), baseMoney));
        assertThat("размер positions мастера не равно", masterPortfolio.getPositions().size(), is(0));
        assertThat("дата  портеля мастера не равно", masterPortfolio.getBaseMoneyPosition().getChangedAt(), is(date));
    }

}

