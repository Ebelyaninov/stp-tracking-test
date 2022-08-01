package socialTrackingClient.handleUnsubscribeCommand;


import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaOldConfiguration;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.*;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.invest.tracking.client.TrackingClient;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_CLIENT_COMMAND;

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

public class HandleUnsubscribeCommandErrorTest {

    UtilsTest utilsTest = new UtilsTest();

    @Autowired
    ClientService clientService;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    ContractService contractService;
    @Autowired
    StringToByteSenderService kafkaSender;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    SubscriptionBlockService subscriptionBlockService;
    @Autowired
    StpSiebel stpSiebel;

    Contract contractSlave;
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


    @SneakyThrows
    @Test
    @AllureId("1903049")
    @DisplayName("1903049 Деактивация подписки со статусом draft")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на деактивацию подписки")
    void C1903049() {
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, ClientRiskProfile.conservative, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub,
            ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative, StrategyStatus.active,
            1, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true);
        //создаем подписку со статусом draft
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(1);
        steps.createSubcription(investIdSlave,ClientRiskProfile.conservative, contractIdSlave,  ContractState.tracked,
            strategyId, SubscriptionStatus.draft, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(1));
        int count = strategyMaster.getSlavesCount();
        //отправляем команду на отписку
        OffsetDateTime time1 = OffsetDateTime.now();
        createCommandClientUnsubscribe(contractIdSlave, strategyId, time1);
        //находим подписку и проверяем по ней данные
        strategyMaster = strategyService.getStrategy(strategyId);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("draft"));
        //находим запись по контракту ведомого и проверяем значения
        contractSlave = contractService.getContract(contractIdSlave);
        assertThat("статус ведомого не равен", contractSlave.getState().toString(), is("tracked"));
        assertThat("стратегия у ведомого не равна", contractSlave.getStrategyId(), is(strategyId));
        //проверяем, что количество подписок на стратегию не изменилось
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(count));
    }


    @SneakyThrows
    @Test
    @AllureId("1903236")
    @DisplayName("1903236 Не существующие значение contractId")
    @Subfeature("Альтернативные сценарии")
    @Description("Операция для обработки команд, направленных на деактивацию подписки")
    void C1903236() {
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, ClientRiskProfile.conservative, contractIdMaster,
            null, ContractState.untracked, strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub,
            ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative, StrategyStatus.active,
            1, LocalDateTime.now(), 1,"0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true);
        //создаем подписку со статусом draft
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(1);
        steps.createSubcription(investIdSlave,ClientRiskProfile.conservative, contractIdSlave,  ContractState.tracked,
            strategyId, SubscriptionStatus.active, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //отправляем команду на отписку
        OffsetDateTime time1 = OffsetDateTime.now();
        createCommandClientUnsubscribe("2049958230", strategyId, time1);
        //находим подписку и проверяем по ней данные
        strategyMaster = strategyService.getStrategy(strategyId);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("active"));
        //находим запись по контракту ведомого и проверяем значения
        contractSlave = contractService.getContract(contractIdSlave);
        assertThat("статус ведомого не равен", contractSlave.getState().toString(), is("tracked"));
        assertThat("стратегия у ведомого не равна", contractSlave.getStrategyId(), is(strategyId));
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
