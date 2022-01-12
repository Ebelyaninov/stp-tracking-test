package stpTrackingApi.deleteSubscription;


import com.google.protobuf.StringValue;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.tinkoff.trading.tracking.Tracking;

import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.*;

@Slf4j
@Epic("deleteSubscription - Удаление подписки на торговую стратегию")
@Feature("TAP-7383")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@Tags({@Tag("stp-tracking-api"), @Tag("deleteSubscription")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class
    })
public class DeleteSubscriptionTest {
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    StpTrackingApiSteps steps;

    SubscriptionApi subscriptionApi = ApiClient.api(ApiClient.Config.apiConfig()).subscription();

    Strategy strategyMaster;
    Contract contractMaster;
    Client clientMaster;
    Client clientSlave;
    Contract contractSlave;
    Subscription subscription;
    String siebelIdMaster = "5-2G2O9XVOR";
    String siebelIdSlave = "1-4P8R6J5";
    String description = "new test стратегия autotest";
    String contractIdMaster;
    UUID investIdMaster;
    UUID investIdSlave;
    UUID strategyId;
    String contractIdSlave;

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdSlave));
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractService.getContract(contractIdSlave));
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investIdSlave));
            } catch (Exception e) {
            }
            try {
                trackingService.deleteStrategy(strategyService.getStrategy(strategyId));
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractService.getContract(contractIdMaster));
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investIdMaster));
            } catch (Exception e) {
            }
        });
    }


    @Test
    @AllureId("535360")
    @DisplayName("C535360.DeleteSubscription.Удаление подписки на торговую стратегию")
    @Subfeature("Успешные сценарии")
    @Description("Метод создания подписки на торговую стратегию ведомым.")
    void C535360() throws Exception {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy11(siebelIdMaster, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 1, LocalDateTime.now());
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave,ClientRiskProfile.conservative, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //находим подписку и проверяем по ней данные

//        contractMaster = contractService.getContract(contractIdMaster);
//        clientMaster = clientService.getClient(investIdMaster);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("active"));
        //находим запись по контракту ведомого и проверяем значения
        contractSlave = contractService.getContract(contractIdSlave);
        assertThat("Роль ведомого не равна null", contractSlave.getRole(), is(nullValue()));
        assertThat("статус ведомого не равен", contractSlave.getState().toString(), is("tracked"));
        assertThat("стратегия у ведомого не равна", contractSlave.getStrategyId(), is(strategyId));
        clientSlave = clientService.getClient(investIdSlave);
        assertThat("номера клиента не равно", clientSlave.getMasterStatus().toString(), is("none"));
        //вычитываем из топика кафкаtracking.subscription.event все offset
        steps.resetOffsetToLate(TRACKING_SUBSCRIPTION_EVENT);
        subscriptionApi.deleteSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_SUBSCRIPTION_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        log.info("Команда в tracking.master.command:  {}", event);
        LocalDateTime dateCreateTr = Instant.ofEpochSecond(event.getCreatedAt().getSeconds(), event.getCreatedAt().getNanos())
            .atZone(ZoneId.of("UTC+3")).toLocalDateTime();

        //проверяем, данные в сообщении
        assertThat("тип события не равен", event.getAction().toString(), is("DELETED"));
        assertThat("ID договора не равен", event.getSubscription().getContractId(), is(contractIdSlave));
        assertThat("ID стратегии не равен", steps.uuid(event.getSubscription().getStrategy().getId()), is(strategyId));

        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(0));
        //находим запись по контракту ведомого и проверяем значения
        contractSlave = contractService.getContract(contractIdSlave);
        assertThat("Роль ведомого не равна null", contractSlave.getRole(), is(nullValue()));
        assertThat("статус ведомого не равен", contractSlave.getState().toString(), is("untracked"));
        assertThat("стратегия у ведомого не равна", contractSlave.getStrategyId(), is(IsNull.nullValue()));
        clientSlave = clientService.getClient(investIdSlave);
        assertThat("номера клиента не равно", clientSlave.getMasterStatus().toString(), is("none"));
        //находим подписку и проверяем по ней данные
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("inactive"));
        assertThat("время удаления подписки не равно", subscription.getEndTime().toLocalDateTime(), is(dateCreateTr));
        strategyMaster = strategyService.getStrategy(strategyId);
    }


    @Test
    @AllureId("1051655")
    @DisplayName("C1051655.DeleteSubscription.Отправка команд при удалении подписки через deleteSubscription" +
        " на списание каждого из типа комиссии (management и result)")
    @Subfeature("Успешные сценарии")
    @Description("Метод создания подписки на торговую стратегию ведомым.")
    void C1051655() throws Exception {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        try {
            contractService.deleteContract(contractService.getContract(contractIdSlave));
        } catch (Exception e) {
        }
        try {
            clientService.deleteClient(clientService.getClient(investIdSlave));
        } catch (Exception e) {
        }
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy11(siebelIdMaster, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 1, LocalDateTime.now());
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave,ClientRiskProfile.conservative, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        //находим подписку и проверяем по ней данные
        strategyMaster = strategyService.getStrategy(strategyId);
//        contractMaster = contractService.getContract(contractIdMaster);
//        clientMaster = clientService.getClient(investIdMaster);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("active"));
        //находим запись по контракту ведомого и проверяем значения
        contractSlave = contractService.getContract(contractIdSlave);
        assertThat("Роль ведомого не равна null", contractSlave.getRole(), is(nullValue()));
        assertThat("статус ведомого не равен", contractSlave.getState().toString(), is("tracked"));
        assertThat("стратегия у ведомого не равна", contractSlave.getStrategyId(), is(strategyId));
        clientSlave = clientService.getClient(investIdSlave);
        assertThat("номера клиента не равно", clientSlave.getMasterStatus().toString(), is("none"));
        //вычитываем из топика кафка tracking.fee.calculate.command все offset
        steps.resetOffsetToLate(TRACKING_FEE_COMMAND);
        Thread.sleep(3000);
        LocalDateTime time = LocalDateTime.now().withNano(0);
        subscriptionApi.deleteSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        List<Pair<String, byte[]>> commands = kafkaReceiver.receiveBatch(TRACKING_FEE_COMMAND, Duration.ofSeconds(30));
        Tracking.ActivateFeeCommand commandeMan = null;
        Tracking.ActivateFeeCommand commandeRes = null;
        String keyMan ="";
        String keyRes ="";
        for (int i = 0; i < commands.size(); i++) {
            Tracking.ActivateFeeCommand commande = Tracking.ActivateFeeCommand.parseFrom(commands.get(i).getValue());
            String commandeKey = commands.get(i).getKey();
            if(commande.getContextCase().getNumber()==3){
                log.info("Команда в tracking.fee.command по management:  {}", commande);
                commandeMan = commande;
                keyMan = commandeKey;
            }
            if(commande.getContextCase().getNumber()==4){
                log.info("Команда в tracking.fee.command по result:  {}", commande);
                commandeRes = commande;
                keyRes = commandeKey;
            }
        }
        //находим подписку и проверяем по ней данные
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        LocalDateTime dateCreateMan = Instant.ofEpochSecond(commandeMan.getCreatedAt().getSeconds())
            .atZone(ZoneId.of("UTC+3")).toLocalDateTime();
        LocalDateTime dateCreateRs = Instant.ofEpochSecond(commandeRes.getCreatedAt().getSeconds())
            .atZone(ZoneId.of("UTC+3")).toLocalDateTime();
        assertThat("ключ команды не равен", keyMan, is(contractIdSlave));
        assertThat("ID подписки не равен", commandeMan.getSubscription().getId(), is(subscription.getId()));
        assertThat("Тип комиссии не равен", commandeMan.getManagement(), is(notNullValue()));
//        assertThat("дата создания команды не равна", dateCreateMan, is(time));
        assertThat("ключ команды не равен", keyRes, is(contractIdSlave));
        assertThat("ID подписки не равен", commandeRes.getSubscription().getId(), is(subscription.getId()));
        assertThat("Тип комиссии не равен", commandeRes.getResult(), is(notNullValue()));
        assertThat("дата создания команды не равна", dateCreateRs, is(time));
    }


    @Test
    @AllureId("1219549")
    @DisplayName("C1219549.DeleteSubscription.Отправка события в топик tracking.contract.event после удаления подписки")
    @Subfeature("Успешные сценарии")
    @Description("Метод создания подписки на торговую стратегию ведомым.")
    void C1219549() throws Exception {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), false);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave,ClientRiskProfile.conservative, contractIdSlave, null, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        strategyMaster = strategyService.getStrategy(strategyId);
//        contractMaster = contractService.getContract(contractIdMaster);
//        clientMaster = clientService.getClient(investIdMaster);
        //находим подписку и проверяем по ней данные
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("active"));
        //находим запись по контракту ведомого и проверяем значения
        contractSlave = contractService.getContract(contractIdSlave);
        assertThat("Роль ведомого не равна null", contractSlave.getRole(), is(nullValue()));
        assertThat("статус ведомого не равен", contractSlave.getState().toString(), is("tracked"));
        assertThat("стратегия у ведомого не равна", contractSlave.getStrategyId(), is(strategyId));
        clientSlave = clientService.getClient(investIdSlave);
        assertThat("номера клиента не равно", clientSlave.getMasterStatus().toString(), is("none"));
        //вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        subscriptionApi.deleteSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
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
}
