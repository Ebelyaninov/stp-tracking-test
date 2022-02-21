package socialTrackingClient.handleAccountRegistrationEvent;

import com.google.protobuf.Timestamp;
import com.vladmihalcea.hibernate.type.range.Range;
import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
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
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaOldConfiguration;
import ru.qa.tinkoff.kafka.oldkafkaservice.OldKafkaService;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingMasterStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingMasterSteps.StpTrackingMasterSteps;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.Tariff.api.TariffApi;
import ru.qa.tinkoff.swagger.Tariff.invoker.ApiClient;
import ru.qa.tinkoff.swagger.Tariff.model.TariffResponseType;
import ru.qa.tinkoff.tariff.configuration.TariffDataBaseAutoConfiguration;
import ru.qa.tinkoff.tariff.entities.ContractTariff;
import ru.qa.tinkoff.tariff.entities.Tariff;
import ru.qa.tinkoff.tariff.services.ContractTariffService;
import ru.qa.tinkoff.tariff.services.TariffService;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.SubscriptionBlock;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.invest.account.event.InvestAccountEvent;
import ru.tinkoff.trading.tracking.Tracking;

import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static ru.qa.tinkoff.kafka.Topics.*;
import static ru.qa.tinkoff.tracking.constants.InvestAccountEventData.*;

@Slf4j
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("handleAccountRegistrationEvent Обработка событий из сервиса счетов")
@Feature("TAP-11008")
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
    StpTrackingMasterStepsConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,

    TariffDataBaseAutoConfiguration.class
})
public class HandleAccountRegistrationEventTest {

    UtilsTest utilsTest = new UtilsTest();

    @Autowired
    ClientService clientService;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    TrackingService trackingService;
    @Autowired
    ContractService contractService;
    @Autowired
    ByteToByteSenderService kafkaSender;
    @Autowired
    OldKafkaService oldKafkaService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StpTrackingMasterSteps stpTrackingMasterSteps;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    StpTrackingSlaveSteps stpTrackingSlaveSteps;
    @Autowired
    SubscriptionBlockService subscriptionBlockService;
    @Autowired
    TariffService tariffService;
    @Autowired
    ContractTariffService contractTariffService;

    TariffApi tariffApi = ApiClient.api(ApiClient.Config.apiConfig()).tariff();

    String SIEBEL_ID_MASTER = "4-1P767GFO";
    String SIEBEL_ID_AGRESSIVE = "5-775DOBIB";
    String SIEBEL_ID_MEDIUM = "5-4HSBIRY7";
    String SIBEL_ID_CONSERVATIVE = "5-LGGA88YZ";
    String contractIdMaster;
    String contractIdAgressive;
    String contractIdMedium;
    String contractIdConservative;
    UUID investIdMaster;
    UUID investIdAgressive;
    UUID investIdMedium;
    UUID investIdCOnservative;
    UUID strategyId = UUID.fromString("7c89e263-58dd-4977-abd7-8228e69a0115");
    String title = "Cтратегия для" + SIEBEL_ID_MASTER;
    String description = "new test стратегия autotest";
    OffsetDateTime time = OffsetDateTime.now();
    java.sql.Timestamp startTime = new java.sql.Timestamp(time.toInstant().toEpochMilli());

    Subscription subscription;
    Contract contractSlave;
    Client clientSlave;

    @BeforeAll
    void getdataFromInvestmentAccount() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountAgressive = steps.getBrokerAccounts(SIEBEL_ID_AGRESSIVE);
        investIdAgressive = resAccountAgressive.getInvestId();
        contractIdAgressive = resAccountAgressive.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountConservative = steps.getBrokerAccounts(SIBEL_ID_CONSERVATIVE);
        investIdCOnservative = resAccountConservative.getInvestId();
        contractIdConservative = resAccountConservative.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountMedium = steps.getBrokerAccounts(SIEBEL_ID_MEDIUM);
        investIdMedium = resAccountMedium.getInvestId();
        contractIdMedium = resAccountMedium.getBrokerAccounts().get(0).getId();
        //Получить id тарифа и обновить на тариф tracking
        Integer tariffID = getTariffIdByTariffType("tracking");
        contractTariffService.updateTariffIdByContract(tariffID, contractIdAgressive, time);
        contractTariffService.updateTariffIdByContract(tariffID, contractIdConservative, time);
        contractTariffService.updateTariffIdByContract(tariffID, contractIdMedium, time);
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {

            try {
                subscriptionBlockService.deleteSubscriptionBlockBySubscriptionId(subscriptionService.getSubscriptionByContract(contractIdAgressive).getId());
            } catch (Exception e) {
            }

            try {
                subscriptionBlockService.deleteSubscriptionBlockBySubscriptionId(subscriptionService.getSubscriptionByContract(contractIdMedium).getId());
            } catch (Exception e) {
            }

            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdAgressive));
            } catch (Exception e) {
            }

            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdMedium));
            } catch (Exception e) {
            }

            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdConservative));
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
                contractService.deleteContract(contractService.getContract(contractIdMedium));
            } catch (Exception e) {
            }

            try {
                contractService.deleteContract(contractService.getContract(contractIdConservative));
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

            try {
                clientService.deleteClient(clientService.getClient(investIdMedium));
            } catch (Exception e) {
            }

            try {
                clientService.deleteClient(clientService.getClient(investIdCOnservative));
            } catch (Exception e) {
            }
        });
    }


    @Test
    @AllureId("1214109")
    @DisplayName("C1214109. Договор не подключается к автоследованию для события (CREATED)(BROKER)(NEW)(Aggressive)")
    @Subfeature("Успешные сценарии")
    @Description("Обработка событий из сервиса счетов")
    void C1214109() {
        //Форимируем и отправляем событие в топик account.registration.event
        byte[] eventBytes = createMessageForHandleAccountRegistrationEventWithOutStrategy(actionCreated, contractIdAgressive, typeBroker, statusNew, investIdAgressive, SIEBEL_ID_AGRESSIVE).toByteArray();
        byte[] keyBytes = createMessageForHandleAccountRegistrationEventWithOutStrategy(actionCreated, contractIdAgressive, typeBroker, statusNew, investIdAgressive, SIEBEL_ID_AGRESSIVE).getId().toByteArray();
        oldKafkaService.send(ACCOUNT_REGISTRATION_EVENT, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(10))
            .until(() -> clientService
                .findClient(investIdAgressive).isPresent());
        checkClient(investIdAgressive, ClientRiskProfile.aggressive);
        checkContract(contractIdAgressive, investIdAgressive, ContractState.untracked, null);
        //Проверяем, что не добавили подписку
        assertThat("Нашли подписку", subscriptionService.findSubcription(contractIdAgressive), is(Optional.empty()));
    }

    @SneakyThrows
    @Test
    @AllureId("1214114")
    @DisplayName("С1214114. Сохранение подписки для договора в статусе OPENED для события (UPDATED)(BROKER)(OPENED)(Conservative)")
    @Subfeature("Успешные сценарии")
    @Description("Обработка событий из сервиса счетов")
    void С1214114() {
        //Добавляем стратегию мастеру
        steps.createClientWintContractAndStrategyWithProfile(SIEBEL_ID_MASTER, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, false);

        //Форимируем и отправляем событие в OffsetDateTime топик account.registration.event
        byte[] eventBytes = createMessageForHandleAccountRegistrationEvent(actionUpdated, contractIdConservative, typeBroker, statusOpened, investIdCOnservative, SIBEL_ID_CONSERVATIVE, strategyId).toByteArray();
        byte[] keyBytes = createMessageForHandleAccountRegistrationEvent(actionCreated, contractIdConservative, typeBroker, statusOpened, investIdCOnservative, SIBEL_ID_CONSERVATIVE, strategyId).getId().toByteArray();
        //вычитываем все события из топика
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        oldKafkaService.send(ACCOUNT_REGISTRATION_EVENT, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(5))
            .until(() -> subscriptionService.findSubcription(contractIdConservative).isPresent());
        await().atMost(Duration.ofSeconds(5))
            .until(() -> subscriptionService.findSubcription(contractIdConservative).get().getStatus(), equalTo(SubscriptionStatus.active));
        //Проверить добавления записи в client
        checkClient(investIdCOnservative, ClientRiskProfile.conservative);
        //Проверяем добавление записи в contract
        checkContract(contractIdConservative, investIdCOnservative, ContractState.tracked, strategyId);

        //Проверяем, что добавили активную подписку
        Date dateNowOne = new Date(System.currentTimeMillis());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd' 'HH");
        String dateNow = (formatter.format(dateNowOne));

        checkSubscription(contractIdConservative, strategyId, SubscriptionStatus.active, false);
        //Проверить актуализируем кол-во подписчиков на стратегию
        assertThat("Slaves_count != 1", strategyService.getStrategy(strategyId).getSlavesCount(), equalTo(1));

        //Ищем и проверяем событие в топике tracking.contract.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event registrationMessage = Tracking.Event.parseFrom(message.getValue());
        String key = message.getKey();
        log.info("Команда в tracking.slave.command:  {}", registrationMessage);
        Instant createAt = Instant.ofEpochSecond(registrationMessage.getCreatedAt().getSeconds(), registrationMessage.getCreatedAt().getNanos());
        //проверяем событие
        checkMessage(registrationMessage, contractIdConservative, "UPDATED", "TRACKED");
    }

    @SneakyThrows
    @Test
    @AllureId("1214117")
    @DisplayName("C1214117. Сохранение подписки для договора в статусе NEW для события (CREATED) (IIS)(NEW)(Medium)")
    @Subfeature("Успешные сценарии")
    @Description("Обработка событий из сервиса счетов")
    void C1214117() {
        //Добавляем стратегию мастеру
        stpTrackingMasterSteps.createClientWithContractAndStrategy(investIdMaster, ClientRiskProfile.moderate, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.moderate,
            StrategyStatus.active, 0, LocalDateTime.now());
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //Форимируем и отправляем событие в топик account.registration.event
        byte[] eventBytes = createMessageForHandleAccountRegistrationEvent(actionCreated, contractIdMedium, typeIIS, statusNew, investIdMedium, SIEBEL_ID_MEDIUM, strategyId).toByteArray();
        byte[] keyBytes = createMessageForHandleAccountRegistrationEvent(actionCreated, contractIdMedium, typeIIS, statusNew, investIdMedium, SIEBEL_ID_MEDIUM, strategyId).getId().toByteArray();
        //вычитываем все события из топика
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        oldKafkaService.send(ACCOUNT_REGISTRATION_EVENT, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(10))
            .until(() -> subscriptionService.findSubcription(contractIdMedium).isPresent());
        //Проверить добавления записи в client
        checkClient(investIdMedium, ClientRiskProfile.moderate);
        //Проверяем добавление записи в contract
        checkContract(contractIdMedium, investIdMedium, ContractState.untracked,null);

        //Проверяем, что добавили  подписку в статусе draft
        String dateNowOnePatern =  "YYYY-MM-dd HH";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateNowOnePatern);
        String dateNow = formatter.format(ZonedDateTime.of(LocalDateTime.now().minusHours(3), ZoneId.of("UTC-0")));

        checkSubscription(contractIdMedium, strategyId, SubscriptionStatus.draft, false);
        //Проверить актуализируем кол-во подписчиков на стратегию
        assertThat("Slaves_count != 1", strategyService.getStrategy(strategyId).getSlavesCount(), equalTo(1));

        //Проверяем, что не отправили событие в топик tracking.contract.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(5));
        assertThat("Отправили событие в топик", messages.size(), equalTo(0));
    }


    @SneakyThrows
    @Test
    @AllureId("1249892")
    @DisplayName("C1249892. Создаем подписку, если client.risk_profile < strategy.risk_profile")
    @Subfeature("Успешные сценарии")
    @Description("Обработка событий из сервиса счетов")
    void C1249892() {
        //Добавляем стратегию мастеру
        stpTrackingMasterSteps.createClientWithContractAndStrategy(investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //Форимируем и отправляем событие в топик account.registration.event
        byte[] eventBytes = createMessageForHandleAccountRegistrationEvent(actionCreated, contractIdMedium, typeBroker, statusNew, investIdMedium, SIEBEL_ID_MEDIUM, strategyId).toByteArray();
        byte[] keyBytes = createMessageForHandleAccountRegistrationEvent(actionCreated, contractIdMedium, typeBroker, statusNew, investIdMedium, SIEBEL_ID_MEDIUM, strategyId).getId().toByteArray();
        oldKafkaService.send(ACCOUNT_REGISTRATION_EVENT, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(10))
            .until(() -> subscriptionService.findSubcription(contractIdMedium).isPresent());
        //Проверить добавления записи в client
        checkClient(investIdMedium, ClientRiskProfile.moderate);
        //Проверяем добавление записи в contract
        checkContract(contractIdMedium, investIdMedium, ContractState.untracked,null);

        //Проверяем, что добавили  подписку в статусе draft
        String dateNowOnePatern =  "YYYY-MM-dd HH";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateNowOnePatern);
        String dateNow = formatter.format(ZonedDateTime.of(LocalDateTime.now().minusHours(3), ZoneId.of("UTC-0")));

        checkSubscription(contractIdMedium, strategyId, SubscriptionStatus.draft, false);
        //Проверить актуализируем кол-во подписчиков на стратегию
        assertThat("Slaves_count != 1", strategyService.getStrategy(strategyId).getSlavesCount(), equalTo(1));

        //Проверяем, что не отправили событие в топик tracking.contract.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(5));
        assertThat("Отправили событие в топик", messages.size(), equalTo(0));
    }

    @SneakyThrows
    @Test
    @AllureId("1214112")
    @DisplayName("C1214112. Нашли подписку в статусе draft и статус договора OPENED (Активация найденной подписки)")
    @Subfeature("Успешные сценарии")
    @Description("Обработка событий из сервиса счетов")
    void C1214112() {
        //Добавляем стратегию мастеру
        stpTrackingMasterSteps.createClientWithContractAndStrategy(investIdMaster, ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //Добавляем подписку slave
        clientSlave = clientService.createClient(investIdAgressive, ClientStatusType.none, null, ClientRiskProfile.aggressive);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractIdAgressive)
            .setClientId(clientSlave.getId())
//            .setRole(null)
            .setState(ContractState.untracked)
            .setStrategyId(null)
            .setBlocked(false);
        contractSlave = contractService.saveContract(contractSlave);
        LocalDateTime now = LocalDateTime.now();
        String periodDefault = "[" + now + ",)";
        Range<LocalDateTime> localDateTimeRange = Range.localDateTimeRange(periodDefault);
        //создаем запись подписке клиента
        subscription = new Subscription()
            .setSlaveContractId(contractIdAgressive)
            .setStrategyId(strategyId)
            .setStartTime(startTime)
            .setStatus(SubscriptionStatus.draft)
            .setEndTime(null)
            .setBlocked(false);
            //.setPeriod(localDateTimeRange);
        subscription = subscriptionService.saveSubscription(subscription);

        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //Форимируем и отправляем событие в топик account.registration.event
        byte[] eventBytes = createMessageForHandleAccountRegistrationEvent(actionUpdated, contractIdAgressive, typeBroker, statusOpened, investIdAgressive, SIEBEL_ID_AGRESSIVE, strategyId).toByteArray();
        byte[] keyBytes = createMessageForHandleAccountRegistrationEvent(actionUpdated, contractIdAgressive, typeBroker, statusOpened, investIdAgressive, SIEBEL_ID_AGRESSIVE, strategyId).getId().toByteArray();
        oldKafkaService.send(ACCOUNT_REGISTRATION_EVENT, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(5))
            .until(() -> subscriptionService.findSubcription(contractIdAgressive).isPresent());
        await().atMost(Duration.ofSeconds(5))
            .until(() -> subscriptionService.findSubscriptionByContractIdAndStatus(contractIdAgressive, SubscriptionStatus.active).getStatus().equals(SubscriptionStatus.active));
        //Для события в статусе open добавляем подписку в статусе draft и не вызывали метод офферов
        checkSubscription(contractIdAgressive, strategyId, SubscriptionStatus.active,  false);
        checkContract(contractIdAgressive, investIdAgressive, ContractState.tracked, strategyId);
        //Проверить актуализируем кол-во подписчиков на стратегию
        assertThat("Slaves_count != 0", strategyService.getStrategy(strategyId).getSlavesCount(), equalTo(0));

        //Проверяем, что не отправили событие в топик tracking.contract.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(5));
        //Ищем и проверяем событие в топике tracking.contract.event
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event registrationMessage = Tracking.Event.parseFrom(message.getValue());
        String key = message.getKey();
        log.info("Команда в tracking.slave.command:  {}", registrationMessage);
        Instant createAt = Instant.ofEpochSecond(registrationMessage.getCreatedAt().getSeconds(), registrationMessage.getCreatedAt().getNanos());
        //проверяем событие
        checkMessage(registrationMessage, contractIdAgressive, "UPDATED", "TRACKED");
    }

    private static Stream<Arguments> provideStringsForSubscriptionStatus() {
        return Stream.of(
            Arguments.of(SubscriptionStatus.draft),
            Arguments.of(SubscriptionStatus.active)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForSubscriptionStatus")
    @AllureId("1214111")
    @DisplayName("С1214111. Нашли подписку в статусе draft и active и статус договора NEW (Нашли клиента в Client)")
    @Subfeature("Успешные сценарии")
    @Description("Обработка событий из сервиса счетов")
    void С1214111(SubscriptionStatus subscriptionStatus) {
        //Добавляем стратегию мастеру
        stpTrackingMasterSteps.createClientWithContractAndStrategy(investIdMaster, ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //Добавляем подписку slave

        stpTrackingSlaveSteps.createSubcription(investIdAgressive, contractIdAgressive, null, ContractState.tracked,
            ClientRiskProfile.aggressive, strategyId, subscriptionStatus, startTime,null,false);
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //Форимируем и отправляем событие в топик account.registration.event
        byte[] eventBytes = createMessageForHandleAccountRegistrationEvent(actionCreated, contractIdAgressive, typeBroker, statusNew, investIdAgressive, SIEBEL_ID_AGRESSIVE, strategyId).toByteArray();
        byte[] keyBytes = createMessageForHandleAccountRegistrationEvent(actionCreated, contractIdAgressive, typeBroker, statusNew, investIdAgressive, SIEBEL_ID_AGRESSIVE, strategyId).getId().toByteArray();
        oldKafkaService.send(ACCOUNT_REGISTRATION_EVENT, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(5))
            .until(() -> subscriptionService.findSubcription(contractIdAgressive).isPresent());
        await().atMost(Duration.ofSeconds(5))
            .until(() -> subscriptionService.findSubcription(contractIdAgressive).get().getStatus().equals(subscriptionStatus));

        checkClient(investIdAgressive, ClientRiskProfile.aggressive);
        checkContract(contractIdAgressive, investIdAgressive, ContractState.tracked, strategyId);
        checkSubscription(contractIdAgressive, strategyId, subscriptionStatus,  false);
        //Проверить актуализируем кол-во подписчиков на стратегию
        assertThat("Slaves_count != 0", strategyService.getStrategy(strategyId).getSlavesCount(), equalTo(0));
    }

    @SneakyThrows
    @Test
    @DisplayName("C1217794. Нашли подписку в статусе active и статус договора OPENED")
    @Subfeature("Успешные сценарии")
    @Description("Обработка событий из сервиса счетов")
    void C1217794() {
        //Добавляем стратегию мастеру
        stpTrackingMasterSteps.createClientWithContractAndStrategy(investIdMaster, ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());

        //Добавляем подписку slave
        stpTrackingSlaveSteps.createSubcription(investIdAgressive, contractIdAgressive, null, ContractState.tracked,
            ClientRiskProfile.aggressive, strategyId, SubscriptionStatus.active, startTime,null,false);

        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //Форимируем и отправляем событие в топик account.registration.event
        byte[] eventBytes = createMessageForHandleAccountRegistrationEvent(actionCreated, contractIdAgressive, typeBroker, statusNew, investIdAgressive, SIEBEL_ID_AGRESSIVE, strategyId).toByteArray();
        byte[] keyBytes = createMessageForHandleAccountRegistrationEvent(actionCreated, contractIdAgressive, typeBroker, statusNew, investIdAgressive, SIEBEL_ID_AGRESSIVE, strategyId).getId().toByteArray();
        oldKafkaService.send(ACCOUNT_REGISTRATION_EVENT, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(5))
            .until(() -> subscriptionService.findSubcription(contractIdAgressive).isPresent());
        await().atMost(Duration.ofSeconds(5))
            .until(() -> subscriptionService.findSubcription(contractIdAgressive).get().getStatus().equals(SubscriptionStatus.active));

        checkClient(investIdAgressive, ClientRiskProfile.aggressive);
        checkContract(contractIdAgressive, investIdAgressive, ContractState.tracked, strategyId);
        checkSubscription(contractIdAgressive, strategyId, SubscriptionStatus.active,  false);
        //Проверить актуализируем кол-во подписчиков на стратегию
        assertThat("Slaves_count != 0", strategyService.getStrategy(strategyId).getSlavesCount(), equalTo(0));
    }

    @SneakyThrows
    @Test
    @AllureId("1249816")
    @DisplayName("С1249816. Блокируем подписку, если risk_profile > strategy.risk_profile")
    @Subfeature("Успешные сценарии")
    @Description("Обработка событий из сервиса счетов")
    void С1249816() {
        //Добавляем стратегию мастеру
        stpTrackingMasterSteps.createClientWithContractAndStrategy(investIdMaster, ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //Форимируем и отправляем событие в топик account.registration.event
        byte[] eventBytes = createMessageForHandleAccountRegistrationEvent(actionUpdated, contractIdMedium, typeBroker, statusNew, investIdMedium, SIEBEL_ID_MEDIUM, strategyId).toByteArray();
        byte[] keyBytes = createMessageForHandleAccountRegistrationEvent(actionUpdated, contractIdMedium, typeBroker, statusNew, investIdMedium, SIEBEL_ID_MEDIUM, strategyId).getId().toByteArray();
        oldKafkaService.send(ACCOUNT_REGISTRATION_EVENT, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(10))
            .until(() -> subscriptionService.findSubcription(contractIdMedium).isPresent());

        checkClient(investIdMedium, ClientRiskProfile.moderate);
        checkContract(contractIdMedium, investIdMedium, ContractState.untracked, null);
        checkSubscription(contractIdMedium, strategyId, SubscriptionStatus.draft,  true);
        checkSubscriptionBlock(contractIdMedium, SubscriptionBlockReason.RISK_PROFILE.getAlias());
        //Проверить актуализируем кол-во подписчиков на стратегию
        assertThat("Slaves_count != 1", strategyService.getStrategy(strategyId).getSlavesCount(), equalTo(1));
    }

    @SneakyThrows
    @Test
    @AllureId("1249817")
    @DisplayName("С1249817. Блокируем подписку если client.risk_profile IS NULL")
    @Subfeature("Успешные сценарии")
    @Description("Обработка событий из сервиса счетов")
    void С1249817() {
        //Добавляем стратегию мастеру
        stpTrackingMasterSteps.createClientWithContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //Добавляем запись в client
        clientService.createClient(investIdAgressive, ClientStatusType.none,null,null);
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //Форимируем и отправляем событие в топик account.registration.event
        byte[] eventBytes = createMessageForHandleAccountRegistrationEvent(actionUpdated, contractIdAgressive, typeBroker, statusOpened, investIdAgressive, SIEBEL_ID_AGRESSIVE, strategyId).toByteArray();
        byte[] keyBytes = createMessageForHandleAccountRegistrationEvent(actionUpdated, contractIdAgressive, typeBroker, statusOpened, investIdAgressive, SIEBEL_ID_AGRESSIVE, strategyId).getId().toByteArray();
        oldKafkaService.send(ACCOUNT_REGISTRATION_EVENT, keyBytes, eventBytes);

        await().atMost(Duration.ofSeconds(5))
            .until(() -> subscriptionService.findSubcription(contractIdAgressive).isPresent());
        await().atMost(Duration.ofSeconds(5))
            .until(() -> subscriptionService.findSubcription(contractIdAgressive).get().getStatus().equals(SubscriptionStatus.active));

        checkSubscription(contractIdAgressive, strategyId, SubscriptionStatus.active,  true);
        checkContract(contractIdAgressive, investIdAgressive, ContractState.tracked, strategyId);
        checkSubscriptionBlock(contractIdAgressive, SubscriptionBlockReason.RISK_PROFILE.getAlias());
        //Проверить актуализируем кол-во подписчиков на стратегию
        assertThat("Slaves_count != 1", strategyService.getStrategy(strategyId).getSlavesCount(), equalTo(1));
    }

    @Test
    @AllureId("1332315")
    @DisplayName("С1332315. Не активируем подписку с типом тарифа clientTariff.type != 'tracking' ")
    @Subfeature("Успешные сценарии")
    @Description("Обработка событий из сервиса счетов")
    void С1332315() {
        //Добавляем стратегию мастеру
        steps.createClientWintContractAndStrategyWithProfile(SIEBEL_ID_MASTER, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, false);

        //updateTariffId(70, contractIdConservative);

        //Изменить тариф клиенту в БД тарифов
        contractTariffService.updateTariffIdByContract(getTariffIdByTariffType("WM"), contractIdConservative, time);

        //Форимируем и отправляем событие в OffsetDateTimeтопик account.registration.event
        byte[] eventBytes = createMessageForHandleAccountRegistrationEvent(actionUpdated, contractIdConservative, typeBroker, statusOpened, investIdCOnservative, SIBEL_ID_CONSERVATIVE, strategyId).toByteArray();
        byte[] keyBytes = createMessageForHandleAccountRegistrationEvent(actionCreated, contractIdConservative, typeBroker, statusOpened, investIdCOnservative, SIBEL_ID_CONSERVATIVE, strategyId).getId().toByteArray();
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        oldKafkaService.send(ACCOUNT_REGISTRATION_EVENT, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(10))
            .until(() -> subscriptionService.findSubcription(contractIdConservative).isPresent());
        //Добавил задержку 3с, для обработки данных приложением(Возможно не сразу получим ответ от тарифного модуля)
        await().atLeast(Duration.ofSeconds(3));
        //Проверить добавления записи в client
        checkClient(investIdCOnservative, ClientRiskProfile.conservative);
        //Проверяем добавление записи в contract
        checkContract(contractIdConservative, investIdCOnservative, ContractState.untracked, null);

        //Проверяем, что добавили подписку в статусе draft
        checkSubscription(contractIdConservative, strategyId, SubscriptionStatus.draft, false);
        //Проверить актуализируем кол-во подписчиков на стратегию
        assertThat("Slaves_count != 1", strategyService.getStrategy(strategyId).getSlavesCount(), equalTo(1));

        //Проверяем, что не отправили событие в топик tracking.contract.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(5));
        assertThat("Отправили событие в топик", messages.size(), equalTo(0));

        //Вернуть тариф tracking клиенту в БД тарифов
        contractTariffService.updateTariffIdByContract(getTariffIdByTariffType("tracking"), contractIdConservative, time);
    }

    //Метод для создания события по схеме  invest-account-event.proto
    InvestAccountEvent.Event createMessageForHandleAccountRegistrationEventWithOutStrategy (int action, String brokerAccountId, int type, int status,
                                                                            UUID investId, String siebelId){

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
                .build())
            .build();
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


    //проверяем параметры команды по синхронизации
    void checkMessage(Tracking.Event registrationMessage, String contractId, String action, String state) {
        assertThat("ID договора ведомого не равен " + contractId, registrationMessage.getContract().getId(), is(contractId));
        assertThat("action не равен " + action, registrationMessage.getAction().toString(), is(action));
        assertThat("state не равно " + state, registrationMessage.getContract().getState().toString(), is(state));
        assertThat("blocked не равно false ", registrationMessage.getContract().getBlocked(), is(false));
    }

    //проверяем данные в client
    void checkClient (UUID investId, ClientRiskProfile riskProfile) {
        Client getDataFromCLient = clientService.getClient(investId);
        assertThat("id != " + investId, getDataFromCLient.getId(), equalTo(investId));
        assertThat("master_status !=" + ClientStatusType.none, getDataFromCLient.getMasterStatus(), equalTo(ClientStatusType.none));
        assertThat("social_profile != null", getDataFromCLient.getSocialProfile(), equalTo(null));
        assertThat("risk_profile !=" + riskProfile, getDataFromCLient.getRiskProfile(), equalTo(riskProfile));

    }

    //проверяем данные в client
    void checkContract (String contractId, UUID investId, ContractState state, UUID strategyId) {
        Contract getDataFromContract = contractService.getContract(contractId);
        assertThat("id != " + contractIdMedium, getDataFromContract.getId(), equalTo(contractId));
        assertThat("client_id  != " + investId, getDataFromContract.getClientId(), equalTo(investId));
//        assertThat("role != null", getDataFromContract.getRole(), equalTo(null));
        assertThat("state != " + state, getDataFromContract.getState(), equalTo(state));
        assertThat("strategy_id != " + strategyId, getDataFromContract.getStrategyId(), equalTo(strategyId));
        //assertThat("blocked != false", getDataFromContract.getBlocked(), equalTo(false));
    }

    //проверяем данные в subscription
    void checkSubscription (String contractId, UUID strategyId, SubscriptionStatus status, Boolean blocked) {
        Date dateNowOne = new Date(System.currentTimeMillis());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd' 'HH");
        String dateNow = (formatter.format(dateNowOne));
        Subscription getSubscription = subscriptionService.getSubscriptionByContract(contractId);
        assertThat("slave_contract_id != " + contractId, getSubscription.getSlaveContractId(), equalTo(contractId));
        assertThat("strategy_id != " + strategyId, getSubscription.getStrategyId(), equalTo(strategyId));
        assertThat("start_time != " + dateNow, getSubscription.getStartTime().toString().substring(0,13), equalTo(dateNow));
        assertThat("status != " + status, getSubscription.getStatus(), equalTo(status));
        assertThat("end_time != null", getSubscription.getEndTime(), equalTo(null));
        assertThat("blocked != " + blocked, getSubscription.getBlocked(), equalTo(blocked));
    }

    //проверяем блокировку подписки
    void checkSubscriptionBlock (String contractId, String subscriptionBlockReason) {
        Date dateNowOne = new Date(System.currentTimeMillis());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
        String dateNow = (formatter.format(dateNowOne));
        SubscriptionBlock getDataFromSubscriptionBlock =  subscriptionBlockService.getSubscriptionBlockBySubscriptionId(subscriptionService.getSubscriptionByContract(contractId).getId());
        assertThat("lower(period) !=  now()" , getDataFromSubscriptionBlock.getPeriod().lower().toString().substring(0, 16), equalTo(dateNow));
        assertThat("upper(period) !=  null" , getDataFromSubscriptionBlock.getPeriod().upper(), nullValue());
        assertThat("upper(period) !=  null" , getDataFromSubscriptionBlock.getPeriod().upper(), nullValue());
        assertThat("subscriptionBlockReason !=  risk-profile" , getDataFromSubscriptionBlock.getReason(), equalTo(subscriptionBlockReason));
    }

    //проверяем блокировку подписки
    Integer getTariffIdByTariffType (String tariffType) {
        Integer getTariffId = tariffService.getTariff()
            .stream()
            .filter(tariff -> tariff.getTariffType().contains(tariffType))
            .collect(Collectors.toList())
            .get(0).getId();
        return getTariffId;
    }

    //Проверяем тариф и обновляем
    void updateTariffId (Integer tariffID, String contractId) {

        var getTariff = tariffApi
            .getTariff()
            .xAppNameHeader("social-tracking-client")
            .contractIdPath(contractId)
            .execute(response -> response.as(TariffResponseType.class));

        if (getTariff.getTariff().getSiebelId() !=  "RD11.0" & getTariff.getTariff().getName() != "Автоследование"){
            contractTariffService.updateTariffIdByContract(tariffID, contractId, time);
        }
    }

}


