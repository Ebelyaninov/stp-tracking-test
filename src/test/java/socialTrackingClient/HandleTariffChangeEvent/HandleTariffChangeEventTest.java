package socialTrackingClient.HandleTariffChangeEvent;

import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
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
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaOldConfiguration;
import ru.qa.tinkoff.kafka.oldkafkaservice.OldKafkaService;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.invest.tariff.ChangeTariff;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.*;

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
    StpTrackingApiStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class
})
public class HandleTariffChangeEventTest {

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
    @Autowired
    StpSiebel stpSiebel;

    String SIEBEL_ID_MASTER;
    String SIEBEL_ID_SLAVE;
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
        SIEBEL_ID_MASTER = stpSiebel.siebelIdMasterForClient;
        SIEBEL_ID_SLAVE = stpSiebel.siebelIdSlaveForClient;
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
    @AllureId("1348238")
    @DisplayName("C1348238. Активация подписки после смены тарифа на TRACKING")
    @Subfeature("Успешные сценарии")
    @Description("Обработка событий об изменении тарифа")
    void C1348238() {
        //Добавляем стратегию мастеру
        steps.createClientWithContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        //Добавляем подписку slave
        clientSlave = clientService.createClient(investIdSlave, ClientStatusType.none, null, ClientRiskProfile.aggressive);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractIdSlave)
            .setClientId(clientSlave.getId())
//            .setRole(null)
            .setState(ContractState.untracked)
            .setStrategyId(null)
            .setBlocked(false);
        contractSlave = contractService.saveContract(contractSlave);
        //создаем запись подписке клиента
        subscription = new Subscription()
            .setSlaveContractId(contractIdSlave)
            .setStrategyId(strategyId)
            .setStartTime(startTime)
            .setStatus(SubscriptionStatus.draft)
            .setEndTime(null)
            .setBlocked(false);
        subscription = subscriptionService.saveSubscription(subscription);

        ChangeTariff.Event buildMessage = ChangeTariff.Event.newBuilder()
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
                        .setSiebelId("TRD10.0")
                        .setType(ChangeTariff.TariffType.TRACKING)
                        .build())
                    .build()))
            .build();
        //вычитываем все события из топика tracking.contract.event
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //Отправляем событие в топик tariff.change.raw
        byte[] eventBytes = buildMessage.toByteArray();
        oldKafkaService.send(TARIFF_CHANGE_RAW, eventBytes, eventBytes);
        //Проверяем обновление подписки
        await().atMost(Duration.ofSeconds(5))
            .until(() -> subscriptionService.findSubcription(contractIdSlave).get().getStatus(), equalTo(SubscriptionStatus.active));
        //Ищем и проверяем событие в топике tracking.contract.event
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event registrationMessage = Tracking.Event.parseFrom(message.getValue());
        log.info("Команда в tracking.slave.command:  {}", registrationMessage);
        //проверяем событие
        assertThat("ID договора ведомого не равен " + contractIdSlave, registrationMessage.getContract().getId(), is(contractIdSlave));
        assertThat("action не равен UPDATED", registrationMessage.getAction(), is(Tracking.Event.Action.UPDATED));
        assertThat("state не равно tracked", registrationMessage.getContract().getState(), is(Tracking.Contract.State.TRACKED));
        assertThat("blocked не равно false ", registrationMessage.getContract().getBlocked(), is(false));
        //Проверяем обновление подписки
        Optional<Subscription> getDataFromSubscription = subscriptionService.findSubcription(contractIdSlave);
        assertThat("Не активировали подписку", getDataFromSubscription.get().getStatus(), is(SubscriptionStatus.active));
        //Проверить обновление контракта
        Optional<Contract> getDataFromContract = contractService.findContract(contractIdSlave);
        assertThat("state != tracked", getDataFromContract.get().getState(), is(ContractState.tracked));
        assertThat("Не обновили strategyId", getDataFromContract.get().getStrategyId(), is(getDataFromSubscription.get().getStrategyId()));
    }

}
