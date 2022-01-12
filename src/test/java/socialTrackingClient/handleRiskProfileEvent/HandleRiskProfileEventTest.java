package socialTrackingClient.handleRiskProfileEvent;


import com.google.protobuf.Timestamp;;
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
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
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
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tariff.configuration.TariffDataBaseAutoConfiguration;
import ru.qa.tinkoff.tariff.services.ContractTariffService;
import ru.qa.tinkoff.tariff.services.TariffService;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.SubscriptionBlock;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.repositories.SubscriptionBlockRepository;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.invest.testing.risk.notification.event.TestingRiskNotification;
import ru.tinkoff.trading.tracking.Tracking;

import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.*;

@Slf4j
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("handleRiskProfileEvent Обработка событий об изменении риск-профиля клиента")
@Feature("TAP-10863")
@DisplayName("social-tracking-client")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Owner("ext.ebelyaninov")
@Tags({@Tag("social-tracking-client"),@Tag("handleRiskProfileEvent")})
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
public class HandleRiskProfileEventTest {

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
    SubscriptionBlockRepository subscriptionBlockRepository;

    String SIEBEL_ID_MASTER = "1-3HRG6FI";
    //String SIEBEL_ID_AGRESSIVE = "5-775DOBIB";
    String SIEBEL_ID_AGRESSIVE = "3-2VE3QMJ9M";
    String SIEBEL_ID_MEDIUM = "5-4HSBIRY7";
    String SIBEL_ID_CONSERVATIVE = "5-LGGA88YZ";
    String contractIdMaster;
    String contractIdAgressive;
    String contractIdMedium;
    String contractIdConservative;
    Long secondContractId;
    Long thirdContractId;
    Long fourthContracrId;
    UUID investIdMaster;
    UUID investIdAgressive;
    UUID investIdMedium;
    UUID investIdCOnservative;
    UUID strategyId = UUID.fromString("7c89e263-58dd-4977-abd7-8228e69a0115");
    String title = "Cтратегия для" + SIEBEL_ID_MASTER;
    String description = "new test стратегия autotest";
    OffsetDateTime time = OffsetDateTime.now();
    java.sql.Timestamp startTime = new java.sql.Timestamp(time.toInstant().toEpochMilli());
    String periodDefoult;

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
        secondContractId = Long.valueOf(contractIdConservative) + 1;
        thirdContractId = Long.valueOf(contractIdConservative) + 2;
        fourthContracrId = Long.valueOf(contractIdConservative) + 3;
        LocalDate currentDate = (LocalDate.now());
        periodDefoult = "[" + currentDate + ",)";
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
                subscriptionBlockService.deleteSubscriptionBlockBySubscriptionId(subscriptionService.getSubscriptionByContract(contractIdConservative).getId());
            } catch (Exception e) {
            }

            try {
                subscriptionBlockService.deleteSubscriptionBlockBySubscriptionId(subscriptionService.getSubscriptionByContract(String.valueOf(secondContractId)).getId());
            } catch (Exception e) {
            }

            try {
                subscriptionBlockService.deleteSubscriptionBlockBySubscriptionId(subscriptionService.getSubscriptionByContract(String.valueOf(thirdContractId)).getId());
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
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(String.valueOf(secondContractId)));
            } catch (Exception e) {
            }

            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(String.valueOf(thirdContractId)));
            } catch (Exception e) {
            }

            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(String.valueOf(fourthContracrId)));
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
                contractService.deleteContract(contractService.getContract(String.valueOf(secondContractId)));
            } catch (Exception e) {
            }

            try {
                contractService.deleteContract(contractService.getContract(String.valueOf(thirdContractId)));
            } catch (Exception e) {
            }

            try {
                contractService.deleteContract(contractService.getContract(String.valueOf(fourthContracrId)));
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


    @SneakyThrows
    @Test
    @AllureId("1253785")
    @DisplayName("C1253785. Текущее значение client.risk_profile = type из события")
    @Subfeature("Успешные сценарии")
    @Description("handleRiskProfileEvent Обработка событий об изменении риск-профиля клиента")
    void C1253785() {
        //Добавляем стратегию мастеру
        stpTrackingMasterSteps.createClientWithContractAndStrategy(investIdMaster, ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //Добавляем подписку slave
        stpTrackingSlaveSteps.createSubcription(investIdCOnservative, contractIdConservative, null, ContractState.tracked,
            ClientRiskProfile.conservative, strategyId, SubscriptionStatus.active, startTime,null,true);
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //Форимируем и отправляем событие в топик account.registration.event
        byte[] eventBytes = createMessageForhandleRiskProfileEvent(investIdCOnservative, TestingRiskNotification.Event.Type.CONSERVATIVE).toByteArray();
        byte[] keyBytes = createMessageForhandleRiskProfileEvent(investIdCOnservative, TestingRiskNotification.Event.Type.CONSERVATIVE).toByteArray();
        oldKafkaService.send(ORIGINATION_TESTING_RISK_NOTIFICATION_RAW, keyBytes, eventBytes);
        //Проверить, что не изменили risk_profile = conservative
        checkClient(investIdCOnservative, ClientRiskProfile.conservative);
        //Проверяем, что не сняли блокировку с подписки
        checkSubscription(contractIdConservative, strategyId, SubscriptionStatus.active,  true, null);
        //Проверяем, что не отправили событие в топик tracking.contract.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(5));
        assertThat("Отправили событие в топик", messages.size(), equalTo(0));
    }


    @SneakyThrows
    @Test
    @AllureId("1253838")
    @DisplayName("C1253838. Разблокировка подписок после уменьшения риск профиля (strategy.risk_profile > client.risk_profile)")
    @Subfeature("Успешные сценарии")
    @Description("handleRiskProfileEvent Обработка событий об изменении риск-профиля клиента")
    void C1253838() {
        //Добавляем стратегию мастеру
        stpTrackingMasterSteps.createClientWithContractAndStrategy(investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //Добавляем подписку slave
        stpTrackingSlaveSteps.createSubcription(investIdCOnservative, contractIdConservative, null, ContractState.tracked,
            ClientRiskProfile.conservative, strategyId, SubscriptionStatus.active, startTime,null,true);
        //Формируем 2 контракта и подписку
        Contract secondContract =  contractService.getContract(contractIdConservative);
        contractSlave = secondContract
            .setId(secondContractId.toString());
        contractService.saveContract(secondContract);

        Subscription secondSubscription = subscriptionService.getSubscriptionByContract(contractIdConservative);
        subscription = secondSubscription
            .setId(secondSubscription.getId() +1)
            .setSlaveContractId(secondContractId.toString());
        subscriptionService.saveSubscription(subscription);

        Long subscriptionId = subscriptionService.getSubscriptionByContract(contractIdConservative).getId();
        Long secondSubscriptionId = subscriptionService.getSubscriptionByContract(secondContractId.toString()).getId();
        subscriptionBlockService.saveSubscriptionBlock(subscriptionId, SubscriptionBlockReason.RISK_PROFILE, periodDefoult);
        subscriptionBlockService.saveSubscriptionBlock(secondSubscriptionId, SubscriptionBlockReason.RISK_PROFILE, periodDefoult);

        String getLowerPeriod = subscriptionBlockService.getSubscriptionBlockBySubscriptionId(subscriptionId).getPeriod().lower().toString();
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_SUBSCRIPTION_EVENT);
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //Форимируем и отправляем событие в топик account.registration.event
        byte[] eventBytes = createMessageForhandleRiskProfileEvent(investIdCOnservative, TestingRiskNotification.Event.Type.AGGRESSIVE).toByteArray();
        byte[] keyBytes = createMessageForhandleRiskProfileEvent(investIdCOnservative, TestingRiskNotification.Event.Type.AGGRESSIVE).toByteArray();
        oldKafkaService.send(ORIGINATION_TESTING_RISK_NOTIFICATION_RAW, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(5))
            .until(() -> subscriptionService.findSubcription(contractIdConservative).get().getBlocked(), equalTo(false));
        //Проверить, что не изменили risk_profile = aggressive
        checkClient(investIdCOnservative, ClientRiskProfile.aggressive);
        //Проверяем, что  сняли блокировку с подписки
        checkSubscription(contractIdConservative, strategyId, SubscriptionStatus.active,  false, null);
        checkSubscriptionBlock(contractIdConservative, SubscriptionBlockReason.RISK_PROFILE.getAlias(), getLowerPeriod, "now()");
        checkSubscription(secondContractId.toString(), strategyId, SubscriptionStatus.active,  false, null);
        checkSubscriptionBlock(secondContractId.toString(), SubscriptionBlockReason.RISK_PROFILE.getAlias(), getLowerPeriod, "now()");
        //Ищем и проверяем событие в топике tracking.contract.event
        List<Pair<String, byte[]>> messagesFromTrackingSubsctiptionFirst = kafkaReceiver.receiveBatch(TRACKING_SUBSCRIPTION_EVENT, Duration.ofSeconds(20));
        List<Pair<String, byte[]>> messagesFromTrackingSubscriptionSecond = kafkaReceiver.receiveBatch(TRACKING_SUBSCRIPTION_EVENT, Duration.ofSeconds(21));
        List<Pair<String, byte[]>> messagesFromDelayCommand = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(20));
        List<Pair<String, byte[]>> messagesFromDelayCommandSecond = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(21));

        //Проверка 2 событий из топика TRACKING_SUBSCRIPTION_EVENT
        Tracking.Event messageForFirstSubscription;
        Tracking.Event messageForSecondSubscription;

        if (messagesFromTrackingSubsctiptionFirst.get(0).getKey().equals(contractIdConservative)){
            messageForFirstSubscription = filterMessageByKey(messagesFromTrackingSubsctiptionFirst, contractIdConservative);
            checkMessageFromSubscriptionEvent(messageForFirstSubscription, contractIdConservative, "UPDATED", subscriptionId);
        }
        else {
            messageForFirstSubscription = filterMessageByKey(messagesFromTrackingSubsctiptionFirst, secondContractId.toString());
            checkMessageFromSubscriptionEvent(messageForFirstSubscription, secondContractId.toString(), "UPDATED", secondSubscriptionId);
        }

        if (messagesFromTrackingSubscriptionSecond.get(0).getKey().equals(secondContractId.toString())){
            messageForSecondSubscription = filterMessageByKey(messagesFromTrackingSubscriptionSecond, secondContractId.toString());
            checkMessageFromSubscriptionEvent(messageForSecondSubscription, secondContractId.toString(), "UPDATED", secondSubscriptionId);
        }
        else {
            messageForSecondSubscription = filterMessageByKey(messagesFromTrackingSubscriptionSecond, contractIdConservative);
            checkMessageFromSubscriptionEvent(messageForSecondSubscription, contractIdConservative, "UPDATED", subscriptionId);
        }

        //Проверяем 2 события из топика TRACKING_DELAY_COMMAND
        Tracking.PortfolioCommand getFirstDelayedMessage;
        Tracking.PortfolioCommand getSecondDelayedMessage;

        if (messagesFromDelayCommand.get(0).getKey().equals(contractIdConservative)){
            getFirstDelayedMessage = filterMessageByKeyForDelayCommand(messagesFromDelayCommand, contractIdConservative);
            checkMessageFromDelayCommand(getFirstDelayedMessage, contractIdConservative, "RETRY_SYNCHRONIZATION","now()");
        }
        else {
            getFirstDelayedMessage = filterMessageByKeyForDelayCommand(messagesFromDelayCommand, secondContractId.toString());
            checkMessageFromDelayCommand(getFirstDelayedMessage, secondContractId.toString(), "RETRY_SYNCHRONIZATION","now()");
        }

        if (messagesFromDelayCommandSecond.get(0).getKey().equals(secondContractId.toString())){
            getSecondDelayedMessage = filterMessageByKeyForDelayCommand(messagesFromDelayCommandSecond, secondContractId.toString());
            checkMessageFromDelayCommand(getSecondDelayedMessage, secondContractId.toString(), "RETRY_SYNCHRONIZATION","now()");
        }
        else {
            getSecondDelayedMessage = filterMessageByKeyForDelayCommand(messagesFromDelayCommandSecond, contractIdConservative);
            checkMessageFromDelayCommand(getSecondDelayedMessage, contractIdConservative, "RETRY_SYNCHRONIZATION","now()");
        }

    }


    @SneakyThrows
    @Test
    @AllureId("1260888")
    @DisplayName("C1260888. Разблокировка подписок после уменьшения риск профиля (strategy.risk_profile = client.risk_profile)")
    @Subfeature("Успешные сценарии")
    @Description("handleRiskProfileEvent Обработка событий об изменении риск-профиля клиента")
    void C1260888() {
        //Добавляем стратегию мастеру
        stpTrackingMasterSteps.createClientWithContractAndStrategy(investIdMaster, ClientRiskProfile.moderate, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.moderate,
            StrategyStatus.active, 0, LocalDateTime.now());
        //Добавляем подписку slave
        stpTrackingSlaveSteps.createSubcription(investIdMedium, contractIdMedium, null, ContractState.tracked,
            ClientRiskProfile.conservative, strategyId, SubscriptionStatus.active, startTime,null,true);

        LocalDate currentDate = (LocalDate.now());
        String periodDefoult = "[" + currentDate + ",)";
        Long subscriptionId = subscriptionService.getSubscriptionByContract(contractIdMedium).getId();
        subscriptionBlockService.saveSubscriptionBlock(subscriptionId, SubscriptionBlockReason.RISK_PROFILE, periodDefoult);
        String getLowerPeriod = subscriptionBlockService.getSubscriptionBlockBySubscriptionId(subscriptionId).getPeriod().lower().toString();
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_SUBSCRIPTION_EVENT);
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //Форимируем и отправляем событие в топик account.registration.event
        byte[] eventBytes = createMessageForhandleRiskProfileEvent(investIdMedium, TestingRiskNotification.Event.Type.AGGRESSIVE).toByteArray();
        byte[] keyBytes = createMessageForhandleRiskProfileEvent(investIdMedium, TestingRiskNotification.Event.Type.AGGRESSIVE).toByteArray();
        oldKafkaService.send(ORIGINATION_TESTING_RISK_NOTIFICATION_RAW, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(5))
            .until(() -> subscriptionService.findSubcription(contractIdMedium).get().getBlocked(), equalTo(false));
        //Проверить, что не изменили risk_profile = aggressive
        checkClient(investIdMedium, ClientRiskProfile.aggressive);
        //Проверяем, что  сняли блокировку с подписки
        checkSubscription(contractIdMedium, strategyId, SubscriptionStatus.active,  false, null);
        checkSubscriptionBlock(contractIdMedium, SubscriptionBlockReason.RISK_PROFILE.getAlias(), getLowerPeriod, "now()");
        //Ищем и проверяем событие в топике tracking.contract.event
        List<Pair<String, byte[]>> messagesFromTrackingSubsctiptionFirst = kafkaReceiver.receiveBatch(TRACKING_SUBSCRIPTION_EVENT, Duration.ofSeconds(20));
        List<Pair<String, byte[]>> messagesFromDelayCommand = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(20));

        Tracking.Event messageForSubscription = filterMessageByKey(messagesFromTrackingSubsctiptionFirst, contractIdMedium);
        checkMessageFromSubscriptionEvent(messageForSubscription, contractIdMedium, "UPDATED", subscriptionId);

        Tracking.PortfolioCommand getFirstDelayedMessage = filterMessageByKeyForDelayCommand(messagesFromDelayCommand, contractIdMedium);
        checkMessageFromDelayCommand(getFirstDelayedMessage, contractIdMedium, "RETRY_SYNCHRONIZATION","now()");
    }

    @SneakyThrows
    @Test
    @AllureId("1253805")
    @DisplayName("C1253805. Блокировка подписок при увеличение риска профиля")
    @Subfeature("Успешные сценарии")
    @Description("handleRiskProfileEvent Обработка событий об изменении риск-профиля клиента")
    void C1253805() {
        //Добавляем стратегию мастеру
        stpTrackingMasterSteps.createClientWithContractAndStrategy(investIdMaster, ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //Добавляем подписку slave
        stpTrackingSlaveSteps.createSubcription(investIdMedium, contractIdMedium, null, ContractState.tracked,
            ClientRiskProfile.aggressive, strategyId, SubscriptionStatus.active, startTime,null,false);

        Long subscriptionId = subscriptionService.getSubscriptionByContract(contractIdMedium).getId();
        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_SUBSCRIPTION_EVENT);
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //Форимируем и отправляем событие в топик account.registration.event
        byte[] eventBytes = createMessageForhandleRiskProfileEvent(investIdMedium, TestingRiskNotification.Event.Type.MODERATE).toByteArray();
        byte[] keyBytes = createMessageForhandleRiskProfileEvent(investIdMedium, TestingRiskNotification.Event.Type.MODERATE).toByteArray();
        oldKafkaService.send(ORIGINATION_TESTING_RISK_NOTIFICATION_RAW, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(5))
            .until(() -> subscriptionService.findSubcription(contractIdMedium).get().getBlocked(), equalTo(true));
        //Проверить, что изменили risk_profile = moderate
        checkClient(investIdMedium, ClientRiskProfile.moderate);
        //Проверяем, что  заблокировали подписку
        checkSubscription(contractIdMedium, strategyId, SubscriptionStatus.active,  true, null);
        SubscriptionBlock getDataFromSubscriptionBlock =  subscriptionBlockService.getSubscriptionBlockBySubscriptionId(subscriptionService.getSubscriptionByContract(contractIdMedium).getId());
        assertThat("lower(period) !=  now()" , getDataFromSubscriptionBlock.getPeriod().lower().toString().substring(0,21), equalTo(time.toString().substring(0,21)));
        assertThat("upper(period) !=  ", getDataFromSubscriptionBlock.getPeriod().upper(), equalTo(null));
        assertThat("subscriptionBlockReason !=  risk-profile" , getDataFromSubscriptionBlock.getReason(), equalTo(SubscriptionBlockReason.RISK_PROFILE.getAlias()));
        //Ищем и проверяем событие в топике tracking.contract.event
        List<Pair<String, byte[]>> messagesFromTrackingSubsctiptionFirst = kafkaReceiver.receiveBatch(TRACKING_SUBSCRIPTION_EVENT, Duration.ofSeconds(20));
        List<Pair<String, byte[]>> messagesFromDelayCommand = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(20));

        Tracking.Event messageForSubscription = filterMessageByKey(messagesFromTrackingSubsctiptionFirst, contractIdMedium);
        checkMessageFromSubscriptionEvent(messageForSubscription, contractIdMedium, "UPDATED", subscriptionId);
    }


    @SneakyThrows
    @Test
    @AllureId("1253916")
    @DisplayName("C1253916. Не обновили записи во время блокировки подписки")
    @Subfeature("Успешные сценарии")
    @Description("handleRiskProfileEvent Обработка событий об изменении риск-профиля клиента")
    void C1253916() {
        //Добавляем стратегию мастеру
        stpTrackingMasterSteps.createClientWithContractAndStrategy(investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //Добавляем первую подписку slave в статусе draft
        stpTrackingSlaveSteps.createSubcription(investIdCOnservative, contractIdConservative, null, ContractState.tracked,
            ClientRiskProfile.aggressive, strategyId, SubscriptionStatus.draft, startTime,null,false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdConservative);
        Long subscriptionIdFirst = subscription.getId();
        //Добавиляем вторую подписку (blocked)
        contractSlave = contractService.getContract(contractIdConservative);
        contractService.saveContract(contractSlave.setId(secondContractId.toString()).setStrategyId(strategyId));
        subscription
            .setBlocked(true)
            .setId(subscriptionIdFirst + 1)
            .setStatus(SubscriptionStatus.active)
            .setStrategyId(strategyId)
            .setSlaveContractId(secondContractId.toString());
        subscriptionService.saveSubscription(subscription);
        //добавить активную подписку
        contractService.saveContract(contractSlave.setId(thirdContractId.toString()));
        subscription
            .setBlocked(false)
            .setId(subscriptionIdFirst + 2)
            .setStrategyId(strategyId)
            .setSlaveContractId(thirdContractId.toString());
        subscriptionService.saveSubscription(subscription);
        //добавить  подписку (inactive)
        contractService.saveContract(contractSlave.setId(fourthContracrId.toString()));
        OffsetDateTime time = OffsetDateTime.now();
        java.sql.Timestamp endTime = new java.sql.Timestamp(time.toInstant().toEpochMilli());
        subscription
            .setBlocked(false)
            .setStatus(SubscriptionStatus.inactive)
            .setId(subscriptionIdFirst + 3)
            .setStrategyId(strategyId)
            .setEndTime(endTime)
            .setSlaveContractId(fourthContracrId.toString());
        subscriptionService.saveSubscription(subscription);

        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_SUBSCRIPTION_EVENT);
        //Форимируем и отправляем событие в топик account.registration.event
        byte[] eventBytes = createMessageForhandleRiskProfileEvent(investIdCOnservative, TestingRiskNotification.Event.Type.CONSERVATIVE).toByteArray();
        byte[] keyBytes = createMessageForhandleRiskProfileEvent(investIdCOnservative, TestingRiskNotification.Event.Type.CONSERVATIVE).toByteArray();
        oldKafkaService.send(ORIGINATION_TESTING_RISK_NOTIFICATION_RAW, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(5))
            .until(() -> clientService.findClient(investIdCOnservative).get().getRiskProfile(), equalTo(ClientRiskProfile.conservative));
        //Проверить, что изменили risk_profile = conservative
        checkClient(investIdCOnservative, ClientRiskProfile.conservative);
        //Проверяем, что не заблокировали подписки
        checkSubscription(contractIdConservative, strategyId, SubscriptionStatus.draft,  false, null);
        //Подписка была в статусе blocked
        checkSubscription(secondContractId.toString(), strategyId, SubscriptionStatus.active,  true, null);
        checkSubscription(thirdContractId.toString(), strategyId, SubscriptionStatus.active,  false, null);
        checkSubscription(fourthContracrId.toString(), strategyId, SubscriptionStatus.inactive,  false, endTime);
        //Проверить, что не добавили запись в subscripTionBlock
        List<String> listOfContracts = new ArrayList<>();
        listOfContracts.add(contractIdConservative);
        listOfContracts.add(secondContractId.toString());
        listOfContracts.add(thirdContractId.toString());
        listOfContracts.add(fourthContracrId.toString());
        //Проверяем, что не отправили событие в топик tracking.contract.event
        List<Pair<String, byte[]>> messagesFomSubscription = kafkaReceiver.receiveBatch(TRACKING_SUBSCRIPTION_EVENT, Duration.ofSeconds(5));
        assertThat("Отправили событие в топик", messagesFomSubscription.size(), equalTo(0));
    }

    @SneakyThrows
    @Test
    @AllureId("1253948")
    @DisplayName("C1253948. Не обновили заблокированные подписки во время разблокировки")
    @Subfeature("Успешные сценарии")
    @Description("handleRiskProfileEvent Обработка событий об изменении риск-профиля клиента")
    void C1253948() {
        //Добавляем стратегию мастеру
        stpTrackingMasterSteps.createClientWithContractAndStrategy(investIdMaster, ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //Добавляем первую Не заблокированую подписку в статусе draft
        stpTrackingSlaveSteps.createSubcription(investIdCOnservative, contractIdConservative, null, ContractState.tracked,
            ClientRiskProfile.conservative, strategyId, SubscriptionStatus.draft, startTime,null,true);
        subscription = subscriptionService.getSubscriptionByContract(contractIdConservative);
        Long subscriptionIdFirst = subscription.getId();
        //Добавиляем вторую подписку (не заблокированя)
        contractSlave = contractService.getContract(contractIdConservative);
        contractService.saveContract(contractSlave.setId(secondContractId.toString()).setStrategyId(strategyId));
        subscription
            .setBlocked(false)
            .setId(subscriptionIdFirst + 1)
            .setStatus(SubscriptionStatus.active)
            .setStrategyId(strategyId)
            .setSlaveContractId(secondContractId.toString());
        subscriptionService.saveSubscription(subscription);
        //добавить активную заблокированую подписку
        contractService.saveContract(contractSlave.setId(thirdContractId.toString()));
        subscription
            .setBlocked(true)
            .setId(subscriptionIdFirst + 2)
            .setStrategyId(strategyId)
            .setSlaveContractId(thirdContractId.toString());
        subscriptionService.saveSubscription(subscription);
        subscriptionBlockService.saveSubscriptionBlock(subscriptionIdFirst + 2, SubscriptionBlockReason.RISK_PROFILE, periodDefoult);
        //добавить  подписку (inactive)
        contractService.saveContract(contractSlave.setId(fourthContracrId.toString()));
        OffsetDateTime time = OffsetDateTime.now();
        java.sql.Timestamp endTime = new java.sql.Timestamp(time.toInstant().toEpochMilli());
        subscription
            .setStatus(SubscriptionStatus.inactive)
            .setId(subscriptionIdFirst + 3)
            .setStrategyId(strategyId)
            .setEndTime(endTime)
            .setSlaveContractId(fourthContracrId.toString());
        subscriptionService.saveSubscription(subscription);
        String getLowerPeriod = subscriptionBlockService.getSubscriptionBlockBySubscriptionId(subscriptionIdFirst + 2).getPeriod().lower().toString();

        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_SUBSCRIPTION_EVENT);
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //Форимируем и отправляем событие в топик account.registration.event
        byte[] eventBytes = createMessageForhandleRiskProfileEvent(investIdCOnservative, TestingRiskNotification.Event.Type.CONSERVATIVE).toByteArray();
        byte[] keyBytes = createMessageForhandleRiskProfileEvent(investIdCOnservative, TestingRiskNotification.Event.Type.CONSERVATIVE).toByteArray();
        oldKafkaService.send(ORIGINATION_TESTING_RISK_NOTIFICATION_RAW, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(5))
            .until(() -> clientService.findClient(investIdCOnservative).get().getRiskProfile(), equalTo(ClientRiskProfile.conservative));
        //Проверить, что изменили risk_profile = conservative
        checkClient(investIdCOnservative, ClientRiskProfile.conservative);
        //Проверяем, что не обновили записи
        checkSubscription(contractIdConservative, strategyId, SubscriptionStatus.draft,  true, null);
        checkSubscription(secondContractId.toString(), strategyId, SubscriptionStatus.active,  false, null);
        checkSubscription(thirdContractId.toString(), strategyId, SubscriptionStatus.active,  true, null);
        checkSubscription(fourthContracrId.toString(), strategyId, SubscriptionStatus.inactive,  true, endTime);

        checkSubscriptionBlock(thirdContractId.toString(), SubscriptionBlockReason.RISK_PROFILE.getAlias(), getLowerPeriod,null);
        //Проверяем, что не отправили событие в топик tracking.contract.event
        List<Pair<String, byte[]>> messagesFomSubscription = kafkaReceiver.receiveBatch(TRACKING_SUBSCRIPTION_EVENT, Duration.ofSeconds(5));
        assertThat("Отправили событие в топик", messagesFomSubscription.size(), equalTo(0));
        List<Pair<String, byte[]>> messagesFromDelayCommand = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(5));
        assertThat("Отправили событие в топик", messagesFromDelayCommand.size(), equalTo(0));
    }


    private static Stream<Arguments> provideStringsForRiskProfile () {
        return Stream.of(
            //Блокировка slave.aggressive ->  slave.moderate
            Arguments.of(ClientRiskProfile.aggressive, StrategyRiskProfile.aggressive, ClientRiskProfile.aggressive, TestingRiskNotification.Event.Type.MODERATE, ClientRiskProfile.moderate),
            //Разблокировка slave.moderate -> slave.agressive
            Arguments.of(ClientRiskProfile.aggressive, StrategyRiskProfile.aggressive, ClientRiskProfile.moderate, TestingRiskNotification.Event.Type.AGGRESSIVE, ClientRiskProfile.aggressive)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForRiskProfile")
    @AllureId("1266921")
    @DisplayName("C1266921. C1266928 Не нашли запись во время разблокировки и блокировки (нет записи в subscription)")
    @Subfeature("Успешные сценарии")
    @Description("handleRiskProfileEvent Обработка событий об изменении риск-профиля клиента")
    void C1266921(ClientRiskProfile masterRiskProfile, StrategyRiskProfile strategyRiskProfile, ClientRiskProfile slaveRiskProfile, TestingRiskNotification.Event.Type riskProfileForUpdate, ClientRiskProfile riskProfileAfterUpdate) {
        //Добавляем стратегию мастеру
        stpTrackingMasterSteps.createClientWithContractAndStrategy(investIdMaster, masterRiskProfile, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, strategyRiskProfile,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investIdCOnservative, ClientStatusType.none, null, slaveRiskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractIdConservative)
            .setClientId(clientSlave.getId())
            .setRole(null)
            .setState(ContractState.tracked)
            .setStrategyId(strategyId)
            .setBlocked(false);
        contractSlave = contractService.saveContract(contractSlave);

        //вычитываем все события из топика tracking.fee.calculate.command
        steps.resetOffsetToLate(TRACKING_SUBSCRIPTION_EVENT);
        steps.resetOffsetToLate(TRACKING_DELAY_COMMAND);
        //Форимируем и отправляем событие в топик account.registration.event
        byte[] eventBytes = createMessageForhandleRiskProfileEvent(investIdCOnservative, riskProfileForUpdate).toByteArray();
        byte[] keyBytes = createMessageForhandleRiskProfileEvent(investIdCOnservative, riskProfileForUpdate).toByteArray();
        oldKafkaService.send(ORIGINATION_TESTING_RISK_NOTIFICATION_RAW, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(5))
            .until(() -> clientService.findClient(investIdCOnservative).get().getRiskProfile(), equalTo(riskProfileAfterUpdate));
        //Проверить, что изменили risk_profile
        checkClient(investIdCOnservative, riskProfileAfterUpdate);
        //Проверяем, что не отправили событие в топик tracking.contract.event
        List<Pair<String, byte[]>> messagesFomSubscription = kafkaReceiver.receiveBatch(TRACKING_SUBSCRIPTION_EVENT, Duration.ofSeconds(5));
        assertThat("Отправили событие в топик", messagesFomSubscription.size(), equalTo(0));
        List<Pair<String, byte[]>> messagesFromDelayCommand = kafkaReceiver.receiveBatch(TRACKING_DELAY_COMMAND, Duration.ofSeconds(5));
        assertThat("Отправили событие в топик", messagesFromDelayCommand.size(), equalTo(0));
    }

    TestingRiskNotification.Event createMessageForhandleRiskProfileEvent (UUID investId, TestingRiskNotification.Event.Type type){

        return TestingRiskNotification.Event.newBuilder()
            .setId(utilsTest.buildByteString(UUID.randomUUID()))
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setInvestId(utilsTest.buildByteString(investId))
            .setType(type)
            .build();
    }

    //проверяем данные в client
    void checkClient (UUID investId, ClientRiskProfile riskProfile) {
        Client getDataFromCLient = clientService.getClient(investId);
        assertThat("id != " + investId, getDataFromCLient.getId(), equalTo(investId));
        assertThat("master_status !=" + ClientStatusType.none, getDataFromCLient.getMasterStatus(), equalTo(ClientStatusType.none));
        assertThat("social_profile != null", getDataFromCLient.getSocialProfile(), equalTo(null));
        assertThat("risk_profile !=" + riskProfile, getDataFromCLient.getRiskProfile(), equalTo(riskProfile));

    }


    //проверяем данные в subscription
    void checkSubscription (String contractId, UUID strategyId, SubscriptionStatus status, Boolean blocked, java.sql.Timestamp endTime) {
        Date dateNowOne = new Date(System.currentTimeMillis());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd' 'HH");
        String dateNow = (formatter.format(dateNowOne));
        Subscription getSubscription = subscriptionService.getSubscriptionByContract(contractId);
        assertThat("slave_contract_id != " + contractId, getSubscription.getSlaveContractId(), equalTo(contractId));
        assertThat("strategy_id != " + strategyId, getSubscription.getStrategyId(), equalTo(strategyId));
        assertThat("start_time != " + dateNow, getSubscription.getStartTime(), equalTo(startTime));
        assertThat("status != " + status, getSubscription.getStatus(), equalTo(status));
        assertThat("end_time !=" + endTime, getSubscription.getEndTime(), equalTo(endTime));
        assertThat("blocked != " + blocked, getSubscription.getBlocked(), equalTo(blocked));
    }

    //проверяем блокировку подписки
    void checkSubscriptionBlock (String contractId, String subscriptionBlockReason, String lowerPeriod, String upperPeriod) {
        Date dateNowOne = new Date(System.currentTimeMillis());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH");
        String dateNow = (formatter.format(dateNowOne));
        SubscriptionBlock getDataFromSubscriptionBlock =  subscriptionBlockService.getSubscriptionBlockBySubscriptionId(subscriptionService.getSubscriptionByContract(contractId).getId());
        assertThat("lower(period) !=  now()" , getDataFromSubscriptionBlock.getPeriod().lower().toString(), equalTo(lowerPeriod));
        if (upperPeriod != null){
            assertThat("upper(period) !=  " + dateNow, getDataFromSubscriptionBlock.getPeriod().upper().toString().substring(0, 13), equalTo(dateNow));
        }
        else {
            assertThat("upper(period) !=  null", getDataFromSubscriptionBlock.getPeriod().upper(), equalTo(null));
        }
        assertThat("subscriptionBlockReason !=  risk-profile" , getDataFromSubscriptionBlock.getReason(), equalTo(subscriptionBlockReason));
    }

    //проверяем параметры команды по синхронизации
    void checkMessageFromSubscriptionEvent (Tracking.Event registrationMessage, String contractId, String action, Long subscriptionId) {
        assertThat("action не равен " + action, registrationMessage.getAction().toString(), is(action));
        UUID getStrategyId =  utilsTest.getGuidFromByteArray(registrationMessage.getSubscription().getStrategy().getId().toByteArray());
        assertThat("strategyId не равен " + strategyId, getStrategyId, is(strategyId));
        assertThat("ID контракта не равен " + contractId, registrationMessage.getSubscription().getContractId(), is(contractId));
        assertThat("subscriptionId не равно " + subscriptionId, registrationMessage.getSubscription().getId(), is(subscriptionId));
    }

    //проверяем параметры команды по синхронизации
    void checkMessageFromDelayCommand (Tracking.PortfolioCommand registrationMessage, String contractId, String operation, String createdAt) {
        assertThat("ID договора ведомого не равен " + contractId, registrationMessage.getContractId(), is(contractId));
        assertThat("operation не равен " + operation, registrationMessage.getOperation().toString(), is(operation));
//        assertThat("Заголовок destination.topic.name не равен tracking.slave.command" , registrationMessage.getAllFields().get(0).getClass().getHe.toString(), is(operation));
//        assertThat("Заголовок delay.seconds не равен 30" , registrationMessage.getAllFields().get(0).getClass().getHe.toString(), is(operation));
    }

    //проверяем параметры команды по подписке
    @SneakyThrows
    Tracking.Event filterMessageByKey (List<Pair<String, byte[]>> messages, String contractId) {
        Pair<String, byte[]> message = messages.stream()
            .filter(ms ->  ms.getKey().equals(contractId))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event parsedMessage = Tracking.Event.parseFrom(message.getValue());
        log.info("Cобытие  в  racking.subscription.event:  {}", parsedMessage);
        return parsedMessage;
    }


    //проверяем параметры команды по синхронизации
    @SneakyThrows
    Tracking.PortfolioCommand filterMessageByKeyForDelayCommand (List<Pair<String, byte[]>> messages, String contractId) {
        Pair<String, byte[]> message = messages.stream()
            .filter(ms ->  ms.getKey().equals(contractId))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.PortfolioCommand parsedMessage = Tracking.PortfolioCommand.parseFrom(message.getValue());
        log.info("Cобытие  в  racking.subscription.event:  {}", parsedMessage);
        return parsedMessage;
    }
}
