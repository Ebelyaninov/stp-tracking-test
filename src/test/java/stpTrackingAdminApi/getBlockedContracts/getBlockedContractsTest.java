package stpTrackingAdminApi.getBlockedContracts;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;
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
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.model.GetSignalsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.model.BlockedContract;
import ru.qa.tinkoff.swagger.tracking_admin.model.GetBlockedContractsResponse;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.*;

@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Slf4j
@Epic("getBlockedContracts - Получение списка заблокированных контрактов")
@Feature("TAP-14194")
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

public class getBlockedContractsTest {

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
    StpTrackingAdminSteps steps;


    String siebelIdMaster = "5-23AZ65JU2";
    String siebelIdSlave = "4-LQB8FKN";

    String contractIdSlave;
    String contractIdMaster;

    UUID investIdSlave;
    UUID investIdMaster;
    UUID strategyId;

    String xApiKey = "x-api-key";
    String key= "tracking";

    @BeforeAll
    void getDataClients() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
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
    @AllureId("1491521")
    @DisplayName("getBlockedContracts. Успешное получение списка заблокированных контрактов")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка договоров, на которые наложена техническая блокировка.")
    void C1491521() {
        String title = "Autotest" + randomNumber(0,100);
        String description = "Autotest get block contract";
        strategyId = UUID.randomUUID();
/*        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategyNew(siebelIdMaster, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку клиента slave на strategy клиента master
        steps.createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyId);
        //блокируем контракт Slave
        steps.BlockContract(contractIdSlave);*/
        //получаем список заблокированных контрактов
        List <Contract> getAllBlockedContracts = contractService.findAllBlockedContract(true).stream()
           // .sorted(Comparator.comparing(Contract::getId))
            .filter(c -> c.getState().equals(ContractState.tracked))
            .collect(Collectors.toList());
/*        Set<String>  listOfBlockedId = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            listOfBlockedId.add(getAllBlockedContracts.get(i).getId());
        }*/
        ArrayList<Contract> listOfBlockedId = new ArrayList<>(30);
        listOfBlockedId.addAll(getAllBlockedContracts);
        //вызываем метод getBlockedContracts
        GetBlockedContractsResponse getblockedContracts = contractApi.getBlockedContracts()
            .reqSpec(r -> r.addHeader("x-api-key", "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBlockedContractsResponse.class));
        Set<String>  listOfBlockedIdFromGet = new HashSet<>();
        for (int i = 0; i < getblockedContracts.getItems().size(); i++) {
            listOfBlockedIdFromGet.add(getblockedContracts.getItems().get(i).getId());
        }
        //получаем ответ и проверяем
        assertThat("hasNext не равен", getblockedContracts.getHasNext(), is(true));
        //assertThat("cursor не равен", getblockedContracts.getNextCursor(), is());
        assertThat("items не равен", getblockedContracts.getItems().size(), is(30));
        assertThat("Проверка", listOfBlockedId , is(listOfBlockedIdFromGet));

    }

    @SneakyThrows
    @Test
    @AllureId("1491599")
    @DisplayName("getBlockedContracts. Передан limit")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка договоров, на которые наложена техническая блокировка.")
    void C1491599() {
        String title = "Autotest" + randomNumber(0,100);
        String description = "Autotest get block contract";
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategyNew(siebelIdMaster, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        //создаем подписку клиента slave на strategy клиента master
        steps.createSubscriptionSlave(siebelIdSlave, contractIdSlave, strategyId);
        //блокируем контракт Slave
        steps.BlockContract(contractIdSlave);
        //вызываем метод getBlockedContracts
        GetBlockedContractsResponse getblockedContracts = contractApi.getBlockedContracts()
            .reqSpec(r -> r.addHeader("x-api-key", "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking")
            .limitQuery(1)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBlockedContractsResponse.class));
        //получаем ответ и проверяем
        assertThat("hasNext не равен", getblockedContracts.getHasNext(), is(true));
        //assertThat("cursor не равен", getblockedContracts.getNextCursor(), is());
        assertThat("items не равен", getblockedContracts.getItems().size(), is(1));
    }



    public static int randomNumber(int min, int max) {
        int number = min + (int) (Math.random() * max);
        return number;
    }

}
