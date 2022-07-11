package socialTrackingClient.handleUnsubscribeCommand;

import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaOldConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.*;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.invest.tracking.client.TrackingClient;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertTrue;
import static ru.qa.tinkoff.kafka.Topics.*;

@Slf4j
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("handleUnsubscribeCommand Обработка команд на деактивацию подписки на стратегию")
@Feature("ISTAP-5433")
@DisplayName("social-tracking-client")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Owner("b.motin")
@Tags({@Tag("social-tracking-client"),@Tag("handleUnsubscribeCommand")})
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    KafkaOldConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingMasterStepsConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class,
    StpTrackingInstrumentConfiguration.class
})

public class HandleUnsubscribeCommandTest {

    UtilsTest utilsTest = new UtilsTest();

    @Autowired
    ClientService clientService;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    ContractService contractService;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    StringToByteSenderService stringToByteSenderService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    SubscriptionBlockService subscriptionBlockService;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    StpInstrument instrument;

    Contract contractSlave;
    Client clientSlave;
    Strategy strategyMaster;
    String siebelIdMaster;
    String siebelIdSlave;
    String contractIdMaster;
    String contractIdSlave;
    UUID investIdMaster;
    UUID investIdSlave;
    UUID strategyId = UUID.randomUUID();
    String description = "new test стратегия autotest";
    Subscription subscription;

    @BeforeAll
    void getdataFromInvestmentAccount() {
        siebelIdMaster = stpSiebel.siebelIdMasterForClient;
        siebelIdSlave = stpSiebel.siebelIdSlaveForClient;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountAgressive = steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountAgressive.getInvestId();
        contractIdSlave = resAccountAgressive.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(siebelIdMaster);
        steps.deleteDataFromDb(siebelIdSlave);
    }


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {

            try {
                subscriptionBlockService.deleteSubscriptionBlockBySubscriptionId(subscriptionService.getSubscriptionByContract(contractIdSlave).getId());
            } catch (Exception e) {
            }

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


    @Test
    @AllureId("1901516")
    @DisplayName("1901516 Отправка команды на списание комиссии (только result) при удалении подписки через handleUnsubscribeCommand")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на деактивацию подписки")
    void C1901516() throws Exception {
        try {
            contractService.deleteContract(contractService.getContract(contractIdSlave));
        } catch (Exception e) {
        }
        try {
            clientService.deleteClient(clientService.getClient(investIdSlave));
        } catch (Exception e) {
        }
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 1, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.conservative, contractIdSlave,  ContractState.tracked,
            strategyId, SubscriptionStatus.active, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //находим подписку и проверяем по ней данные
        strategyMaster = strategyService.getStrategy(strategyId);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("active"));
        //находим запись по контракту ведомого и проверяем значения
        contractSlave = contractService.getContract(contractIdSlave);
        assertThat("статус ведомого не равен", contractSlave.getState().toString(), is("tracked"));
        assertThat("стратегия у ведомого не равна", contractSlave.getStrategyId(), is(strategyId));
        //вычитываем из топика кафка tracking.fee.calculate.command все offset
        kafkaReceiver.resetOffsetToEnd(TRACKING_FEE_COMMAND);
        //отправляем команду на отписку
        OffsetDateTime time1 = OffsetDateTime.now();
        createCommandClientUnsubscribe(contractIdSlave, strategyId, time1);
        //Находим событие на расчет комиссии
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_FEE_COMMAND, Duration.ofSeconds(20)).stream()
            .filter(key -> key.getKey().equals(contractIdSlave))
            .collect(Collectors.toList());
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.ActivateFeeCommand commande = Tracking.ActivateFeeCommand.parseFrom(message.getValue());
        //Проверяем отправку только 1 события
        assertThat("Нашли не 1 событие", messages.size(), is(1));
        //находим подписку и проверяем по ней данные
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        assertThat("ID подписки не равен", commande.getSubscription().getId(), is(subscription.getId()));
        assertThat("Тип комиссии не равен", commande.getResult(), is(notNullValue()));
    }


    @Test
    @AllureId("1902883")
    @DisplayName("1902883 Отправка события в топик tracking.contract.event после удаления подписки")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на деактивацию подписки")
    void C1902883() throws Exception {
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.conservative, contractIdSlave, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        strategyMaster = strategyService.getStrategy(strategyId);
        //находим подписку и проверяем по ней данные
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("active"));
        //находим запись по контракту ведомого и проверяем значения
        contractSlave = contractService.getContract(contractIdSlave);
//        assertThat("Роль ведомого не равна null", contractSlave.getRole(), is(nullValue()));
        assertThat("статус ведомого не равен", contractSlave.getState().toString(), is("tracked"));
        assertThat("стратегия у ведомого не равна", contractSlave.getStrategyId(), is(strategyId));
        clientSlave = clientService.getClient(investIdSlave);
        assertThat("номера клиента не равно", clientSlave.getMasterStatus().toString(), is("none"));
        //вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //отправляем команду на отписку
        OffsetDateTime time1 = OffsetDateTime.now();
        createCommandClientUnsubscribe(contractIdSlave, strategyId, time1);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Команда в tracking.contract.event:  {}", event);
        LocalDateTime dateCreateTr = Instant.ofEpochSecond(event.getCreatedAt().getSeconds(), event.getCreatedAt().getNanos())
            .atZone(ZoneId.of("UTC+3")).toLocalDateTime();
        //проверяем, данные в сообщении
        assertThat("тип события не равен", event.getAction().toString(), is("UPDATED"));
        assertThat("ID договора не равен", event.getContract().getId(), is(contractIdSlave));
        assertThat("contract.state не равен", event.getContract().getState().toString(), is("UNTRACKED"));
        assertThat("contract.blocked не равен", event.getContract().getBlocked(), is(false));
    }



    @SneakyThrows
    @Test
    @AllureId("1902935")
    @DisplayName("1902935 Удаления подписки с технической блокировкой контракта")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на деактивацию подписки")
    void C1902935() {
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, ClientRiskProfile.conservative, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub,
            ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative, StrategyStatus.active,
            1, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true);
        //создаем подписку с активным статусом, но заблокированную в contract
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(1);
        steps.createSubcription(investIdSlave, ClientRiskProfile.conservative, contractIdSlave,  ContractState.tracked,
            strategyId, SubscriptionStatus.active, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, true);
        //отправляем команду на отписку
        OffsetDateTime time1 = OffsetDateTime.now();
        createCommandClientUnsubscribe(contractIdSlave, strategyId, time1);
        //двигаем Offset в конец очереди сообщений
        kafkaReceiver.resetOffsetToEnd(TRACKING_FEE_COMMAND);
        kafkaReceiver.resetOffsetToEnd(TRACKING_SUBSCRIPTION_EVENT);
        kafkaReceiver.resetOffsetToEnd(TRACKING_CONTRACT_EVENT);
        //Смотрим, сообщение,  в топике kafka tracking.fee.command
        List<Pair<String, byte[]>> messagesFee = kafkaReceiver.receiveBatch(TRACKING_FEE_COMMAND, Duration.ofSeconds(5));
        //прроверяем, что по договору не было сообщений т.к. он был заблокирован
        assertTrue("События по договору в  kafka tracking.fee.command не равно", messagesFee.isEmpty());
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(0));
        //находим запись по контракту ведомого и проверяем значения
        contractSlave = contractService.getContract(contractIdSlave);
        assertThat("статус ведомого не равен", contractSlave.getState().toString(), is("untracked"));
        assertThat("стратегия у ведомого не равна", contractSlave.getStrategyId(), is(IsNull.nullValue()));
        assertThat("флаг блокировки у ведомого не равна", contractSlave.getBlocked(), is(false));
        //находим подписку и проверяем по ней данные
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("inactive"));
        assertThat("время удаления подписки не равно", subscription.getEndTime(), is(notNullValue()));
    }


    @Test
    @AllureId("1902953")
    @DisplayName("1902953 Отправка события при удалении подписки через handleUnsubscribeCommand в топик tracking.subscription.event")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на деактивацию подписки")
    void C1902953() throws Exception {
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.conservative, contractIdSlave, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        strategyMaster = strategyService.getStrategy(strategyId);
        //находим подписку и проверяем по ней данные
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("active"));
        //находим запись по контракту ведомого и проверяем значения
        contractSlave = contractService.getContract(contractIdSlave);
        assertThat("статус ведомого не равен", contractSlave.getState().toString(), is("tracked"));
        assertThat("стратегия у ведомого не равна", contractSlave.getStrategyId(), is(strategyId));
        //вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_SUBSCRIPTION_EVENT);
        //отправляем команду на отписку
        OffsetDateTime time1 = OffsetDateTime.now();
        createCommandClientUnsubscribe(contractIdSlave, strategyId, time1);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SUBSCRIPTION_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Команда в tracking.subscription.event:  {}", event);
        //проверяем, данные в сообщении
        assertThat("Action в событии не равен", event.getAction().toString(), is("DELETED"));
        assertThat("ID стратегии не равен", steps.uuid(event.getSubscription().getStrategy().getId()), is(strategyId));;
        assertThat("ID договора не равен", event.getSubscription().getContractId(), is(contractIdSlave));
    }


    //Методы для класса

    @Step("Формируем команду в формате Protobuf в соответствии со схемой tracking.client.proto: ")
    TrackingClient.ClientCommand createCommandUnsubscribe (String contractIdSlave, UUID strategyId, OffsetDateTime time) {

        return TrackingClient.ClientCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setStrategyId(utilsTest.buildByteString(strategyId))
            .setCreatedAt(com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setOperation(TrackingClient.ClientCommand.Operation.UNSUBSCRIBE)
            .build();
    }


    void createCommandClientUnsubscribe(String contractIdSlave, UUID strategyId, OffsetDateTime time) {
        //создаем команду
        TrackingClient.ClientCommand command = createCommandUnsubscribe(contractIdSlave, strategyId, time);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send(TRACKING_CLIENT_COMMAND, contractIdSlave, eventBytes);
    }


}
