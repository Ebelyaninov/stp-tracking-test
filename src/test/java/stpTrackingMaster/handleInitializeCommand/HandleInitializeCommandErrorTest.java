package stpTrackingMaster.handleInitializeCommand;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingMasterStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
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

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_MASTER_COMMAND;

@Slf4j
@Epic("handleInitializeCommand - Обработка команд по инициализации виртуального портфеля")
@Feature("TAP-8067")
@Tags({@Tag("stp-tracking-master"), @Tag("handleInitializeCommand")})
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
public class HandleInitializeCommandErrorTest {

    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    ProfileService profileService;
    @Autowired
    ClientService clientService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    ru.qa.tinkoff.investTracking.services.MasterPortfolioDaoAdapter masterPortfolioDaoAdapter;
    @Autowired
    ContractService contractService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    StpTrackingMasterSteps steps;
    @Autowired
    StpSiebel stpSiebel;


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
    }

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
    @AllureId("640030")
    @DisplayName("C640030.HandleInitializeCommand. Статус статегии != draft")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на первичную инициализацию виртуального портфеля master'а.")
    void C640030() {
        strategyId = UUID.randomUUID();
        //создаем клиента со стратегией в статусе активная
        steps.createClientWithContractAndStrategy(investId, null, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //формируем событие для топика kafka tracking.master.command
        long unscaled = 3500000;
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
        masterPortfolio = masterPortfolioDaoAdapter.getLatestMasterPortfolio(contractId, strategyId);
        assertThat("найдена запись в masterPortfolio", masterPortfolio, is(nullValue()));
    }


    @SneakyThrows
    @Test
    @AllureId("640028")
    @DisplayName("C640028.HandleInitializeCommand.Не найдена запись по стратегии в strategy")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на первичную инициализацию виртуального портфеля master'а.")
    void C640028() {
        strategyId = UUID.randomUUID();
        //формируем событие для топика kafka tracking.master.command
        long unscaled = 4800000;
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
        masterPortfolio = masterPortfolioDaoAdapter.getLatestMasterPortfolio(contractId, strategyId);
        assertThat("найдена запись в masterPortfolio", masterPortfolio, is(nullValue()));
    }

}
