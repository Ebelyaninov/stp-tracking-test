package stpTrackingAdminApi.blockContract;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
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
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.model.Currency;
import ru.qa.tinkoff.swagger.tracking_admin.api.ContractApi;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.model.UpdateStrategyResponse;
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
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static ru.qa.tinkoff.kafka.Topics.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONString;

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

public class BlockContractErrorTest {

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


    Client clientSlave;
    Contract contractSlave;
    Subscription subscription;

    String siebelIdMaster = "5-CQNPKPNH";
    String siebelIdSlave = "5-22NDYVFEE";
    String contractIdSlave;
    UUID investIdSlave;

    String xApiKey = "x-api-key";
    String key = "tracking";

    String notKey = "summer";
    String notContractIdSlave = "1234567890";


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(subscription);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractSlave);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investIdSlave));
            } catch (Exception e) {
            }
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
        });
    }

    @SneakyThrows
    @Test
    @AllureId("1288208")
    @DisplayName("Блокировка контракта ведомого. Авторизация. Не передан x-app-name")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для наложения технической блокировки на договор ведомого.")
    void C1288208(){
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Autotest block contract false";
        UUID strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        String contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy11(siebelIdMaster, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку клиента slave на strategy клиента master
        steps.createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyId);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        clientSlave = clientService.getClient(investIdSlave);
        contractSlave = contractService.getContract(contractIdSlave);
        //Вычитываем из топика кафка tracking.event все offset
       // steps.resetOffsetToLate(TRACKING_CONTRACT_EVENT);
        //Вызываем метод blockContract
        Response responseBlockContract = contractApi.blockContract()
            .reqSpec(r -> r.addHeader(xApiKey, key))
//           .xAppNameHeader("tracking")
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        JSONObject jsonObject = new JSONObject(responseBlockContract.getBody().asString());
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseBlockContract.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseBlockContract.getHeaders().getValue("x-server-time").isEmpty());
        //Проверяем тело ответа "0344-00-Z99"
        assertThat("код ошибки не равно", errorCode, is("0344-00-Z99"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("1288159")
    @DisplayName("Блокировка контракта ведомого. Авторизация. Передан не корректный x-api-key")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для наложения технической блокировки на договор ведомого.")
    void C1288159(){
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "Autotest block contract false";
        UUID strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        String contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy11(siebelIdMaster, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку клиента slave на strategy клиента master
        steps.createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyId);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        clientSlave = clientService.getClient(investIdSlave);
        contractSlave = contractService.getContract(contractIdSlave);
        //Вызываем метод blockContract
        Response responseBlockContract = contractApi.blockContract()
            .reqSpec(r -> r.addHeader(xApiKey, notKey))
            .xAppNameHeader("tracking")
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response);
    }


    @SneakyThrows
    @Test
    @AllureId("1288379")
    @DisplayName("Блокировка контракта ведомого. Авторизация. Передан не существующий contract_id")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для наложения технической блокировки на договор ведомого.")
    void C1288379(){
        //Вызываем метод blockContract
        Response responseBlockContract = contractApi.blockContract()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("tracking")
            .contractIdPath(notContractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        JSONObject jsonObject = new JSONObject(responseBlockContract.getBody().asString());
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseBlockContract.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseBlockContract.getHeaders().getValue("x-server-time").isEmpty());
        //Проверяем тело ответа
        assertThat("код ошибки не равно", errorCode, is("0344-12-B11"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

}
