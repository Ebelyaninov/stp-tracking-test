package socialTrackingClient.HandleTariffChangeEvent;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.SubscriptionService;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.invest.tariff.ChangeTariff;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.TARIFF_CHANGE_RAW;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_CONTRACT_EVENT;

@Slf4j
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("handleTariffChangeEvent Обработка событий об изменении тарифа")
@Feature("TAP-11008")
@DisplayName("social-tracking-client")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Owner("ext.ebelyaninov")
@Tags({@Tag("social-tracking-client"), @Tag("handleTariffChangeEvent")})
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    KafkaOldConfiguration.class,
    StpTrackingApiStepsConfiguration.class
})
public class HandleTariffChangeEventErrorTest {

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
    ByteArrayReceiverService kafkaReceiver;

    String SIEBEL_ID_MASTER = "4-1NLJQ9HH";
    String SIEBEL_ID_SLAVE = "5-8NL1RLT1";
    String contractIdMaster;
    String contractIdSlave;
    UUID investIdMaster;
    UUID investIdSlave;
    UUID strategyId = UUID.randomUUID();
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
        GetBrokerAccountsResponse resAccountAgressive = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        investIdSlave = resAccountAgressive.getInvestId();
        contractIdSlave = resAccountAgressive.getBrokerAccounts().get(0).getId();
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {

            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdSlave));
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
                contractService.deleteContract(contractService.getContract(contractIdSlave));
            } catch (Exception e) {
            }

            try {
                clientService.deleteClient(clientService.getClient(investIdMaster));
            } catch (Exception e) {
            }

            try {
                clientService.deleteClient(clientService.getClient(investIdSlave));
            } catch (Exception e) {
            }
        });
    }


    @SneakyThrows
    @Test
    @AllureId("1348240")
    @DisplayName("C1348240. Игнорируем событие с new_tariff.type != 'TRACKING'")
    @Subfeature("Альтернативные сценарии")
    @Description("Обработка событий об изменении тарифа")
    void C1348240() {
        //Добавляем стратегию мастеру
        steps.createClientWintContractAndStrategyWithProfile(SIEBEL_ID_MASTER, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, false);
        // создаем запись о договоре клиента в tracking.contract
        createSubcription(investIdSlave, null, contractIdSlave, null, ContractState.untracked,
            strategyId, SubscriptionStatus.draft,  false, startTime, null);

        ChangeTariff.Event buildMessage = createMessageForChangeTariff(contractIdSlave, "TRD7.0", ChangeTariff.TariffType.WEALTH_MANAGEMENT);
        //вычитываем все события из топика tracking.contract.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //Отправляем событие в топик tariff.change.raw
        byte[] eventBytes = buildMessage.toByteArray();
        oldKafkaService.send(TARIFF_CHANGE_RAW, eventBytes, eventBytes);
        //Проверяем обновление подписки
        await().atMost(Duration.ofSeconds(5))
            .until(() -> subscriptionService.findSubcription(contractIdSlave).get().getStatus(), equalTo(SubscriptionStatus.draft));
        //Ищем и проверяем событие в топике tracking.contract.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
        //Проверяем, что не обновили подписку
        Optional<Subscription> getDataFromSubscription = subscriptionService.findSubcription(contractIdSlave);
        assertThat("Не активировали подписку", getDataFromSubscription.get().getStatus(), is(SubscriptionStatus.draft));
        //Проверить, что не обновили контракт
        Optional<Contract> getDataFromContract = contractService.findContract(contractIdSlave);
        assertThat("state != tracked", getDataFromContract.get().getState(), is(ContractState.untracked));
        //проверяем событие
        assertThat("Отправили событие в топик", messages.size(), equalTo(0));
    }


    private static Stream<Arguments> subscriptionStatus() {
        return Stream.of(
            Arguments.of(SubscriptionStatus.inactive, ContractState.untracked),
            Arguments.of(SubscriptionStatus.active, ContractState.tracked)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("subscriptionStatus")
    @AllureId("1348239")
    @DisplayName("C1348239. Не нашли подписку в статусе draft")
    @Subfeature("Альтернативные сценарии")
    @Description("Обработка событий об изменении тарифа")
    void C1348239(SubscriptionStatus subscriptionStatus, ContractState contractState) {
        //Добавляем стратегию мастеру
        steps.createClientWintContractAndStrategyWithProfile(SIEBEL_ID_MASTER, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, false);
        //Добавляем подписку slave
        java.sql.Timestamp endTime = null;
        if (subscriptionStatus.equals(SubscriptionStatus.inactive)){
            startTime = new java.sql.Timestamp(time.minusDays(1).toInstant().toEpochMilli());
            endTime = new java.sql.Timestamp(time.toInstant().toEpochMilli());
        }

        createSubcription(investIdSlave, null, contractIdSlave, null, contractState,
            strategyId, subscriptionStatus,  false, startTime, endTime);

        ChangeTariff.Event buildMessage = createMessageForChangeTariff(contractIdSlave, "TRD10.0", ChangeTariff.TariffType.TRACKING);
        //вычитываем все события из топика tracking.contract.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //Отправляем событие в топик tariff.change.raw
        byte[] eventBytes = buildMessage.toByteArray();
        oldKafkaService.send(TARIFF_CHANGE_RAW, eventBytes, eventBytes);
        //Проверяем обновление подписки
        await().atMost(Duration.ofSeconds(5))
            .until(() -> subscriptionService.findSubcription(contractIdSlave).get().getStatus(), equalTo(subscriptionStatus));
        //Ищем и проверяем событие в топике tracking.contract.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
        //Проверяем, что не обновили подписку
        Optional<Subscription> getDataFromSubscription = subscriptionService.findSubcription(contractIdSlave);
        assertThat("Не активировали подписку", getDataFromSubscription.get().getStatus(), is(subscriptionStatus));
        //Проверить, что не обновили контракт
        Optional<Contract> getDataFromContract = contractService.findContract(contractIdSlave);
        assertThat("state != " + contractState, getDataFromContract.get().getState(), is(contractState));
        //проверяем событие
        assertThat("Отправили событие в топик", messages.size(), equalTo(0));
    }


    public void createSubcription(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                  UUID strategyId, SubscriptionStatus subscriptionStatus, Boolean blocked,java.sql.Timestamp dateStart,
                                  java.sql.Timestamp dateEnd) throws JsonProcessingException {
        clientSlave = clientService.createClient(investId, ClientStatusType.none, null, riskProfile);
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        if (subscriptionStatus.equals(SubscriptionStatus.active)){
            contractSlave.setStrategyId(strategyId);
        }
        contractSlave = contractService.saveContract(contractSlave);

        subscription = new Subscription()
            .setSlaveContractId(contractId)
            .setStrategyId(strategyId)
            .setStartTime(dateStart)
            .setStatus(subscriptionStatus)
            .setEndTime(dateEnd)
            .setBlocked(blocked);
        subscription = subscriptionService.saveSubscription(subscription);
    }

    ChangeTariff.Event createMessageForChangeTariff (String contractIdSlave, String newSiebelId,
                                                     ChangeTariff.TariffType newTariffType){
        return  ChangeTariff.Event.newBuilder()
            .setInvestId(utilsTest.buildByteString(UUID.randomUUID()))
            .setChangeDateTime(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setClient(ChangeTariff.Client.newBuilder()
                .addContract(ChangeTariff.Contract.newBuilder()
                    .setId(contractIdSlave)
                    .setOldTariff(ChangeTariff.Tariff.newBuilder()
                        .setSiebelId("TRD3.1")
                        .setType(ChangeTariff.TariffType.WEALTH_MANAGEMENT)
                        .build())
                    .setNewTariff(ChangeTariff.Tariff.newBuilder()
                        .setSiebelId(newSiebelId)
                        .setType(newTariffType)
                        .build())
                    .build()))
            .build();
    }
}
