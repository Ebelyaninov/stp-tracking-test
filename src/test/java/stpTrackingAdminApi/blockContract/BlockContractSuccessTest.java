package stpTrackingAdminApi.blockContract;


import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
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
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.ContractApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.*;

@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Slf4j
@Epic("blockContract - Блокировка контракта")
@Feature("TAP-12142")
@Subfeature("Успешные сценарии")
@DisplayName("stp-tracking-admin")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    InvestTrackingAutoConfiguration.class
})

public class BlockContractSuccessTest {

    ContractApi contractApi = ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.api(ApiClient.Config.apiConfig()).contract();

    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    BillingService billingService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StpTrackingApiSteps steps;


    Client client;
    Contract contract;
    Subscription subscription;
    Strategy strategy;

    String siebelIdMaster = "5-CQNPKPNH";
    String siebelIdSlave = "5-22NDYVFEE";

    String contractIdSlave;
    String contractIdMaster;

    UUID investIdSlave;
    UUID investIdMaster;
    UUID strategyId;

    String xApiKey = "x-api-key";
    String key= "tracking";

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

/*            try {
                contractService.deleteContract(contract);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(client);
            } catch (Exception e) {
            }*/
        });
    }


    @SneakyThrows
    @Test
    @AllureId("1288017")
    @DisplayName("Блокировка контракта ведомого. Успешная блокировка")
    @Subfeature("Успешные сценарии")
    @Description("Метод для наложения технической блокировки на договор ведомого.")
    void C1288017() {
        String title = "Autotest" + randomNumber(0,100);
        String description = "Autotest block contract true";
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
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку клиента slave на strategy клиента master
        steps.createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyId);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        strategy = strategyService.getStrategy(strategyId);
        client = clientService.getClient(investIdSlave);
        contract = contractService.getContract(contractIdSlave);
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //Вызываем метод blockContract
        contractApi.blockContract()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("tracking")
            .xTcsLoginHeader(siebelIdSlave)
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .sorted(Collections.reverseOrder())
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        //Проверяем, данные в сообщении
        checkEventParams(event, "UPDATED", contractIdSlave, "TRACKED", true);
        //Находим в БД автоследования контракт и проверяем его поля
        checkContractParamDB(contractIdSlave, investIdSlave, null, "tracked", strategyId, true);

    }

    @SneakyThrows
    @Test
    @AllureId("1288706")
    @DisplayName("Блокировка контракта ведомого. Блокировка contract_id Мастера")
    @Subfeature("Успешные сценарии")
    @Description("Метод для наложения технической блокировки на договор ведомого.")
    void C1288706() {
        String title = "Autotest" + randomNumber(0,100);
        String description = "Autotest block contract true";
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //Вычитываем из топика кафка tracking.event все offset
        steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //Вызываем метод blockContract
        contractApi.blockContract()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("tracking")
            .xTcsLoginHeader(siebelIdMaster)
            .contractIdPath(contractIdMaster)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
//        strategy = strategyService.getStrategy(strategyId);
//        client = clientService.getClient(investIdMaster);
//        contract = contractService.getContract(contractIdMaster);
        Contract getDataFromContract = contractService.getContract(contractIdMaster);
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messages = kafkaReceiver.receiveBatch(TRACKING_CONTRACT_EVENT, Duration.ofSeconds(20));
        Pair<String, byte[]> message = messages.stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Сообщений не получено"));
        Tracking.Event event = Tracking.Event.parseFrom(message.getValue());
        //Проверяем, данные в сообщении
        checkEventParams(event, "UPDATED", contractIdMaster, "UNTRACKED", true);
        //Находим в БД автоследования контракт и проверяем его поля
        checkContractParamDB(contractIdMaster, investIdMaster, null, "untracked", null, true);

    }


    //Проверяем параметры события
    void checkEventParams(Tracking.Event event, String action, String ContractIdSlave, String state, boolean blocked) {
        assertThat("Action события не равен", event.getAction().toString(), is(action));
        assertThat("ID договора не равен", (event.getContract().getId()), is(ContractIdSlave));
        assertThat("State не равен Tracked", (event.getContract().getState().toString()), is(state));
        assertThat("Blocked не равен true", (event.getContract().getBlocked()), is(true));

    }

    void checkContractParamDB(String contractId, UUID clientId, String role, String state, UUID strategyId, boolean blocked ) {
        Contract getDataFromContract = contractService.getContract(contractId);
        assertThat("ContractId не равен", getDataFromContract.getId(), is(contractId));
        assertThat("номер клиента не равен", getDataFromContract.getClientId(), is(clientId));
        assertThat("роль в контракте не равна", getDataFromContract.getRole(), is(role));
        assertThat("state не равен", getDataFromContract.getState().toString(), is(state));
        assertThat("ID стратегии не равно", getDataFromContract.getStrategyId(), is(strategyId));
        assertThat("статус блокировки не равен", getDataFromContract.getBlocked(), is(true));
    }

    public static int randomNumber(int min, int max) {

        int number = min + (int) (Math.random() * max);

        return number;
    }


}

