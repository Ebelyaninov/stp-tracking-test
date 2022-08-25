package socialTrackingClient.handleAccountRegistrationEvent;

import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
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
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaOldConfiguration;
import ru.qa.tinkoff.kafka.oldkafkaservice.OldKafkaService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingMasterStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tariff.configuration.TariffDataBaseAutoConfiguration;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.invest.account.event.InvestAccountEvent;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static ru.qa.tinkoff.kafka.Topics.ORIGINATION_ACCOUNT_REGISTRATION_EVENT;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_CONTRACT_EVENT;
import static ru.qa.tinkoff.tracking.constants.InvestAccountEventData.*;
import static ru.qa.tinkoff.tracking.constants.InvestAccountEventData.statusOpened;

@Slf4j
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("handleAccountRegistrationEvent Обработка событий из сервиса счетов")
@Feature("TAP-11008")
@Subfeature("Альтернативные сценарии")
@DisplayName("social-tracking-client")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Owner("ext.ebelyaninov")
@Tags({@Tag("social-tracking-client"),@Tag("handleAccountRegistrationEvent")})
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    KafkaOldConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    TariffDataBaseAutoConfiguration.class,
    ApiCreatorConfiguration.class
})
public class HandleAccountRegistrationEventErrorTest {

    UtilsTest utilsTest = new UtilsTest();

    @Autowired
    ClientService clientService;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    ContractService contractService;
    @Autowired
    OldKafkaService oldKafkaService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    SubscriptionBlockService subscriptionBlockService;
    @Autowired
    StpSiebel stpSiebel;

    String SIEBEL_ID_MASTER;
    String SIEBEL_ID_AGRESSIVE;
    String contractIdMaster;
    String contractIdAgressive;
    UUID investIdMaster;
    UUID investIdAgressive;
    UUID strategyId = UUID.randomUUID();
    String title = "Cтратегия для" + SIEBEL_ID_MASTER;
    String description = "new test стратегия autotest";
    OffsetDateTime time = OffsetDateTime.now();
    java.sql.Timestamp startTime = new java.sql.Timestamp(time.toInstant().toEpochMilli());

    Contract contractSlave;
    Client clientSlave;
    Strategy strategy;

    @BeforeAll
    void getdataFromInvestmentAccount() {
        SIEBEL_ID_MASTER = stpSiebel.siebelIdMasterForClient;
        SIEBEL_ID_AGRESSIVE = stpSiebel.siebelIdAgressiveForClient;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountAgressive = steps.getBrokerAccounts(SIEBEL_ID_AGRESSIVE);
        investIdAgressive = resAccountAgressive.getInvestId();
        contractIdAgressive = resAccountAgressive.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(SIEBEL_ID_AGRESSIVE);
        steps.deleteDataFromDb(SIEBEL_ID_MASTER);
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {

            try {
                subscriptionBlockService.deleteSubscriptionBlockBySubscriptionId(subscriptionService.getSubscriptionByContract(contractIdAgressive).getId());
            } catch (Exception e) {
            }

            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdAgressive));
            } catch (Exception e) {
            }

            try {
                strategyService.deleteStrategy(strategyService.getStrategy(strategyId));
            } catch (Exception e) {
            }

            try {
                contractService.deleteContract(contractService.getContract(contractIdMaster));
            } catch (Exception e) {
            }

            try {
                contractService.deleteContract(contractService.getContract(contractIdAgressive));
            } catch (Exception e) {
            }

            try {
                clientService.deleteClient(clientService.getClient(investIdMaster));
            } catch (Exception e) {
            }

            try {
                clientService.deleteClient(clientService.getClient(investIdAgressive));
            } catch (Exception e) {
            }
        });
    }


    private static Stream<Arguments> provideSubscriptionStatusAndStrategyStatus() {
        return Stream.of(
            Arguments.of(InvestAccountEvent.Event.Action.DELETED_VALUE, InvestAccountEvent.BrokerAccount.Type.BROKER_VALUE, InvestAccountEvent.BrokerAccount.Status.OPENED_VALUE),
            Arguments.of(InvestAccountEvent.Event.Action.CREATED_VALUE, InvestAccountEvent.BrokerAccount.Type.INVEST_BOX_VALUE, InvestAccountEvent.BrokerAccount.Status.OPENED_VALUE),
            Arguments.of(InvestAccountEvent.Event.Action.CREATED_VALUE, InvestAccountEvent.BrokerAccount.Type.BROKER_VALUE, InvestAccountEvent.BrokerAccount.Status.CLOSED_VALUE)
        );
    }

    @ParameterizedTest
    @MethodSource("provideSubscriptionStatusAndStrategyStatus")
    @AllureId("1214103")
    @DisplayName("C1214103. Не обрабатываем событие, если не выполнились условия")
    @Description("Обработка событий из сервиса счетов")
    void C1214103(int eventAction, int brokerAccountType, int brokerAccountStatus) {
        //Добавляем стратегию мастеру
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);

        //Форимируем и отправляем событие в OffsetDateTime топик account.registration.event
        InvestAccountEvent.Event event = createMessageForHandleAccountRegistrationEvent(eventAction, contractIdAgressive, brokerAccountType, brokerAccountStatus, investIdAgressive, SIEBEL_ID_AGRESSIVE, strategyId);
        byte[] eventBytes = event.toByteArray();
        byte[] keyBytes = event.getId().toByteArray();
        //вычитываем все события из топика
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        oldKafkaService.send(ORIGINATION_ACCOUNT_REGISTRATION_EVENT, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(3)).pollDelay(Duration.ofNanos(500))
            .until(() -> subscriptionService.findSubcription(contractIdAgressive).isEmpty());
        List<Client> clients = clientService.findClinetsByInvestId(investIdAgressive);
        assertThat("Добавили запись в таблицу client", clients.size(), equalTo(0));
    }



    @Test
    @AllureId("1219337")
    @DisplayName("C1219337. Не нашли запрашиваемую активную стратегию")
    @Description("Обработка событий из сервиса счетов")
    void C1219337() {
        //Добавляем стратегию мастеру
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);

        //Форимируем и отправляем событие в OffsetDateTime топик account.registration.event
        InvestAccountEvent.Event event = createMessageForHandleAccountRegistrationEvent(actionUpdated, contractIdAgressive, typeBroker, statusOpened, investIdAgressive, SIEBEL_ID_AGRESSIVE, strategyId);
        byte[] eventBytes = event.toByteArray();
        byte[] keyBytes = event.getId().toByteArray();
        oldKafkaService.send(ORIGINATION_ACCOUNT_REGISTRATION_EVENT, keyBytes, eventBytes);

        await().atMost(Duration.ofSeconds(3)).pollDelay(Duration.ofNanos(500))
            .until(() -> subscriptionService.findSubcription(contractIdAgressive).isEmpty());

        List<Subscription> getSubscriptions = subscriptionService.getSubscriptionByStrategy(strategyId);
        assertThat("Добавили запись в таблицу client", getSubscriptions.size(), equalTo(0));
        //Удаляем стратегию и повторно отправляем событие
        strategy = strategyService.findStrategyByContractId(contractIdMaster).get();
        strategyService.deleteStrategy(strategy);

        oldKafkaService.send(ORIGINATION_ACCOUNT_REGISTRATION_EVENT, keyBytes, eventBytes);

        await().atMost(Duration.ofSeconds(3)).pollDelay(Duration.ofNanos(500))
            .until(() -> subscriptionService.findSubcription(contractIdAgressive).isEmpty());

        List<Subscription> getSubscriptionsAfterClear = subscriptionService.getSubscriptionByStrategy(strategyId);
        assertThat("Добавили запись в таблицу client", getSubscriptionsAfterClear.size(), equalTo(0));
    }


    @Test
    @AllureId("1216782")
    @DisplayName("C1216782.  Метод tradingApi ответил не статус кодом 200")
    @Description("Обработка событий из сервиса счетов")
    void C1216782() {
        //Форимируем и отправляем событие в OffsetDateTime топик account.registration.event
        InvestAccountEvent.Event event = createMessageForHandleAccountRegistrationEvent(actionUpdated, contractIdAgressive, typeBroker, statusOpened, investIdAgressive, SIEBEL_ID_AGRESSIVE + "2222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222222",
            strategyId);
        byte[] eventBytes = event.toByteArray();
        byte[] keyBytes = event.getId().toByteArray();

        oldKafkaService.send(ORIGINATION_ACCOUNT_REGISTRATION_EVENT, keyBytes, eventBytes);

        await().atMost(Duration.ofSeconds(3)).pollDelay(Duration.ofNanos(500))
            .until(() -> contractService.findContract(contractIdAgressive).isPresent());

        Client getClient = clientService.getClient(investIdAgressive);
        assertThat("Добавили запись в таблицу client c riskProfile != null", getClient.getRiskProfile(), equalTo(null));
    }

    @Test
    @AllureId("1219395")
    @SneakyThrows
    @DisplayName("C1219395. Договор привязан к другой стратегии")
    @Description("Обработка событий из сервиса счетов")
    void C1219395() {
        UUID randomStrategyId = UUID.randomUUID();
        //Добавляем стратегию мастеру
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);

        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investIdAgressive, ClientStatusType.none, null, ClientRiskProfile.aggressive);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractIdAgressive)
            .setClientId(clientSlave.getId())
            .setState(ContractState.tracked)
            .setStrategyId(strategyId)
            .setStrategyId(randomStrategyId)
            .setBlocked(false);
        contractSlave = contractService.saveContract(contractSlave);
        //Форимируем и отправляем событие в OffsetDateTime топик account.registration.event
        InvestAccountEvent.Event event = createMessageForHandleAccountRegistrationEvent(actionUpdated, contractIdAgressive, typeBroker, statusOpened, investIdAgressive, SIEBEL_ID_AGRESSIVE, strategyId);
        byte[] eventBytes = event.toByteArray();
        byte[] keyBytes = event.getId().toByteArray();
        oldKafkaService.send(ORIGINATION_ACCOUNT_REGISTRATION_EVENT, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(3)).pollDelay(Duration.ofNanos(500))
            .until(() -> subscriptionService.findSubcription(contractIdAgressive).isPresent());
        Subscription subscription = subscriptionService.findSubcription(contractIdAgressive).get();
        assertThat("Активировали подписку", subscription.getStatus(), equalTo(SubscriptionStatus.draft));
    }


    @Test
    @AllureId("1216793")
    @DisplayName("C1216793. Tracking APi не вернул riskProfile")
    @Description("Обработка событий из сервиса счетов")
    void C1216793() {
        //Добавляем стратегию мастеру
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);

        //Форимируем и отправляем событие в OffsetDateTime топик account.registration.event
        InvestAccountEvent.Event event = createMessageForHandleAccountRegistrationEvent(actionUpdated, contractIdAgressive, typeBroker, statusOpened, investIdAgressive, SIEBEL_ID_AGRESSIVE.concat("228"), strategyId);
        byte[] eventBytes = event.toByteArray();
        byte[] keyBytes = event.getId().toByteArray();
        oldKafkaService.send(ORIGINATION_ACCOUNT_REGISTRATION_EVENT, keyBytes, eventBytes);

        await().atMost(Duration.ofSeconds(3)).pollDelay(Duration.ofNanos(500)).ignoreExceptions()
            .until(() -> contractService.findContract(contractIdAgressive).isPresent());

        Client client = clientService.getClient(investIdAgressive);
        assertThat("riskProfile != null", client.getRiskProfile(), equalTo(null));
    }


    InvestAccountEvent.Event createMessageForHandleAccountRegistrationEvent (int action, String brokerAccountId, int type, int status,
                                                                             UUID investId, String siebelId, UUID strategyId){

        return InvestAccountEvent.Event.newBuilder()
            .setId(utilsTest.buildByteString(UUID.randomUUID()))
            .setActionValue(action)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setBrokerAccount(InvestAccountEvent.BrokerAccount.newBuilder()
                .setId(brokerAccountId)
                .setTypeValue(type)
                .setStatusValue(status)
                .setOpenedAt(Timestamp.newBuilder()
                    .setSeconds(time.toEpochSecond())
                    .build())
                .setInvestAccount(InvestAccountEvent.InvestAccount.newBuilder()
                    .setId(utilsTest.buildByteString(investId))
                    .setSiebelId(siebelId)
                    .build())
                .setTracking(InvestAccountEvent.Tracking.newBuilder()
                    .setStrategyId(utilsTest.buildByteString(strategyId))
                    .build())
                .build())
            .build();
    }
}
