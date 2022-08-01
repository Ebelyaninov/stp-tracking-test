package stpTrackingAdminApi.getBlockedContracts;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.ContractApiAdminCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.services.database.*;

import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Slf4j
@Epic("getBlockedContracts - Получение списка заблокированных контрактов")
@Feature("TAP-14194")
@Subfeature("Альтернативные сценарии")
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("getBlockedContracts")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    ContractApiAdminCreator.class,
    ApiCreatorConfiguration.class

})

public class getBlockedContractsErrorTest {
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
    StpTrackingAdminSteps steps;
    @Autowired
    StpSiebel siebel;
    @Autowired
    ContractApiAdminCreator contractApiAdminCreator;


    String contractIdSlave;
    String contractIdMaster;

    UUID investIdSlave;
    UUID investIdMaster;
    UUID strategyId;

    String xApiKey = "x-api-key";
    String key = "tracking";
    String notKey = "counter";
    String keyRead = "tcrm";

    @BeforeAll
    void getDataClients() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebel.siebelIdMasterAdmin);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebel.siebelIdSlaveAdmin);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(siebel.siebelIdSlaveAdmin);
        steps.deleteDataFromDb(siebel.siebelIdMasterAdmin);
    }

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

    @SneakyThrows
    @Test
    @AllureId("1491578")
    @DisplayName("getBlockedContracts. Не передан X-API-KEY")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка договоров, на которые наложена техническая блокировка.")
    void C1491578() {
        //вызываем метод getBlockedContracts
        contractApiAdminCreator.get().getBlockedContracts()
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking")
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response);
    }

    @SneakyThrows
    @Test
    @AllureId("1491608")
    @DisplayName("getBlockedContracts. Не передан x-app-name")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка договоров, на которые наложена техническая блокировка.")
    void C1491608() {
        //вызываем метод getBlockedContracts
        Response getblockedContracts = contractApiAdminCreator.get().getBlockedContracts()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xTcsLoginHeader("tracking")
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        JSONObject jsonObject = new JSONObject(getblockedContracts.getBody().asString());
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(getblockedContracts.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(getblockedContracts.getHeaders().getValue("x-server-time").isEmpty());
        //Проверяем тело ответа
        assertThat("код ошибки не равно", errorCode, is("0344-00-Z99"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("1491580")
    @DisplayName("getBlockedContracts. Не передан x-tcs-login")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка договоров, на которые наложена техническая блокировка.")
    void C1491580() {
        //вызываем метод getBlockedContracts
        Response getblockedContracts = contractApiAdminCreator.get().getBlockedContracts()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        JSONObject jsonObject = new JSONObject(getblockedContracts.getBody().asString());
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(getblockedContracts.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(getblockedContracts.getHeaders().getValue("x-server-time").isEmpty());
        //Проверяем тело ответа
        assertThat("код ошибки не равно", errorCode, is("0344-00-Z99"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("1705706")
    @DisplayName("getBlockedContracts. Передан X-API-KEY некорректный")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка договоров, на которые наложена техническая блокировка.")
    void C1705706() {
        //вызываем метод getBlockedContracts
        contractApiAdminCreator.get().getBlockedContracts()
            .reqSpec(r -> r.addHeader(xApiKey, notKey))
            .xAppNameHeader("invest")
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response);
    }

    @SneakyThrows
    @Test
    @AllureId("1705705")
    @DisplayName("getBlockedContracts.Передан X-API-KEYс доступом read")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка договоров, на которые наложена техническая блокировка.")
    void C1705705() {
        //вызываем метод getBlockedContracts
        contractApiAdminCreator.get().getBlockedContracts()
            .reqSpec(r -> r.addHeader(xApiKey, keyRead))
            .xAppNameHeader("invest")
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response);
    }
}
