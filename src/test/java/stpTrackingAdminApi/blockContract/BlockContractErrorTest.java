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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.ContractApiAdminCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.model.ErrorResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.ContractApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;




@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Slf4j
@Epic("blockContract - Блокировка контракта")
@Feature("TAP-12142")
@Subfeature("Успешные сценарии")
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("blockContract")})
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

public class BlockContractErrorTest {
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
    @Autowired
    StpSiebel siebel;
    @Autowired
    ContractApiAdminCreator contractApiAdminCreator;



    String contractIdSlave;
    String contractIdMaster;
    UUID investIdSlave;
    UUID investIdMaster;
    UUID strategyId;
    String title;
    String description;
    String xApiKey = "x-api-key";
    String key = "tracking";
    String notKey = "summer";
    String keyRead = "tcrm";
    String notContractIdSlave = "1234567890";

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
        steps.deleteDataFromDb(siebel.siebelIdMasterAdmin);
        steps.deleteDataFromDb(siebel.siebelIdSlaveAdmin);
    }

    @BeforeEach
    void getStrategyData(){
        title = "Autotest" + randomNumber(0,100);
        description = "Autotest getOrders";
        strategyId = UUID.randomUUID();
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
    @AllureId("1288208")
    @DisplayName("Блокировка контракта ведомого. Авторизация. Не передан x-app-name")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для наложения технической блокировки на договор ведомого.")
    void C1288208(){
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebel.siebelIdMasterAdmin, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1,"0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true);
        //создаем подписку клиента slave на strategy клиента master
        steps.createSubcription(investIdSlave, ClientRiskProfile.conservative, contractIdSlave, ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(OffsetDateTime.now().toInstant().getEpochSecond()), null, false, false);
        //Вызываем метод blockContract
        Response responseBlockContract = contractApiAdminCreator.get().blockContract()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        ErrorResponse errorResponse = responseBlockContract.as(ErrorResponse.class);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseBlockContract.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseBlockContract.getHeaders().getValue("x-server-time").isEmpty());
        //Проверяем тело ответа "0344-00-Z99"
        assertThat("код ошибки не равно", errorResponse.getErrorCode(), is("0344-00-Z99"));
        assertThat("Сообщение об ошибке не равно", errorResponse.getErrorMessage(), is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("1288159")
    @DisplayName("Блокировка контракта ведомого. Авторизация. Передан не корректный x-api-key")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для наложения технической блокировки на договор ведомого.")
    void C1288159(){
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebel.siebelIdMasterAdmin, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1,"0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true);
        //создаем подписку клиента slave на strategy клиента master
        steps.createSubcription(investIdSlave, ClientRiskProfile.conservative, contractIdSlave,ContractState.tracked, strategyId, SubscriptionStatus.active, new java.sql.Timestamp(OffsetDateTime.now().toInstant().getEpochSecond()), null, false, false);
        //Вызываем метод blockContract
        contractApiAdminCreator.get().blockContract()
            .reqSpec(r -> r.addHeader(xApiKey, notKey))
            .xAppNameHeader("tracking")
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response);
    }

    @SneakyThrows
    @Test
    @AllureId("1705429")
    @DisplayName("Блокировка контракта ведомого. Авторизация.Передан x-api-key с доступом read")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для наложения технической блокировки на договор ведомого.")
    void C1705429(){
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebel.siebelIdMasterAdmin, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now(), 1,"0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true);
        //создаем подписку клиента slave на strategy клиента master
        steps.createSubcription(investIdSlave, ClientRiskProfile.conservative, contractIdSlave,
            ContractState.tracked, strategyId, SubscriptionStatus.active,
            new java.sql.Timestamp(OffsetDateTime.now().toInstant().getEpochSecond()),
            null, false, false);
        //Вызываем метод blockContract
        contractApiAdminCreator.get().blockContract()
            .reqSpec(r -> r.addHeader(xApiKey, keyRead))
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
        Response responseBlockContract = contractApiAdminCreator.get().blockContract()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("tracking")
            .xTcsLoginHeader("tracking")
            .contractIdPath(notContractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        ErrorResponse errorResponse = responseBlockContract.as(ErrorResponse.class);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseBlockContract.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseBlockContract.getHeaders().getValue("x-server-time").isEmpty());
        //Проверяем тело ответа
        assertThat("код ошибки не равно", errorResponse.getErrorCode(), is("0344-12-B11"));
        assertThat("Сообщение об ошибке не равно", errorResponse.getErrorMessage(), is("Сервис временно недоступен"));
    }


    public static int randomNumber(int min, int max) {

        int number = min + (int) (Math.random() * max);

        return number;
    }
}
