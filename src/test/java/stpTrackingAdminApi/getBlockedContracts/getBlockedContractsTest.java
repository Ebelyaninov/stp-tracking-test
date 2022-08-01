package stpTrackingAdminApi.getBlockedContracts;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
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
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.model.GetBlockedContractsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Slf4j
@Epic("getBlockedContracts - Получение списка заблокированных контрактов")
@Feature("TAP-14194")
@Subfeature("Успешные сценарии")
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

public class getBlockedContractsTest {
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
    Integer defaultLimit = 30;
    String xApiKey = "x-api-key";
    String key= "tracking";
    String description = "Autotest get block contract";

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
        int limit = 60;
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebel.siebelIdMasterAdmin, investIdMaster, null, contractIdMaster,  ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, new BigDecimal(10.00), "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        //создаем подписку клиента slave на strategy клиента master
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,null,
            ContractState.tracked, strategyId,true, SubscriptionStatus.active,
            new java.sql.Timestamp(OffsetDateTime.now().toInstant().getEpochSecond()),null,false);
        //получаем список заблокированных контрактов из БД, которые подписаны на стратегию
        List <Contract> getAllBlockedContracts = contractService.findAllBlockedContract(true).stream()
            .filter(c -> c.getState().equals(ContractState.tracked))
            .collect(Collectors.toList());
        String nextCursor = "";
        SortedSet<String>  listOfBlockedId = new TreeSet<>();
        for (int i = 0; i < getAllBlockedContracts.size(); i++) {
            listOfBlockedId.add(getAllBlockedContracts.get(i).getId());
            if ( getAllBlockedContracts.size() > limit) {
                nextCursor = getAllBlockedContracts.get(limit-1).getId();
            }
            else {
                nextCursor = listOfBlockedId.last();
            }
        }
        //вызываем метод getBlockedContracts
        GetBlockedContractsResponse getblockedContracts = contractApiAdminCreator.get().getBlockedContracts()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking")
            .limitQuery(limit)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBlockedContractsResponse.class));
        SortedSet<String>  listOfBlockedIdFromGet = new TreeSet<>();
        for (int i = 0; i < getblockedContracts.getItems().size(); i++) {
            listOfBlockedIdFromGet.add(getblockedContracts.getItems().get(i).getId());
        }
        Boolean next = true;
        int contractBlockSize = limit;
        if (listOfBlockedId.size() <= limit) {
            contractBlockSize = listOfBlockedId.size();
        }
        //получаем ответ и проверяем
        assertThat("hasNext не равен", getblockedContracts.getHasNext(), is(next));
        assertThat("cursor не равен", getblockedContracts.getNextCursor(), is(nextCursor));
        assertThat("items не равен", getblockedContracts.getItems().size(), is(contractBlockSize));
        assertThat("договора нет в списке заблокированных", listOfBlockedIdFromGet.contains(contractIdSlave), is(true));
    }

    @SneakyThrows
    @Test
    @AllureId("1491599")
    @DisplayName("getBlockedContracts. Передан limit")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка договоров, на которые наложена техническая блокировка.")
    void C1491599() {
        //получаем список заблокированных контрактов из БД, которые подписаны на стратегию
        List <Contract> getAllBlockedContracts = contractService.findAllBlockedContract(true).stream()
            .filter(c -> c.getState().equals(ContractState.tracked))
            .collect(Collectors.toList());
        SortedSet<String>  listOfBlockedId = new TreeSet<>();
        String nextCursor = "";
        for (int i = 0; i < 1; i++) {
            listOfBlockedId.add(getAllBlockedContracts.get(i).getId());
        }
        //вызываем метод getBlockedContracts
        GetBlockedContractsResponse getblockedContracts = contractApiAdminCreator.get().getBlockedContracts()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking")
            .limitQuery(1)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBlockedContractsResponse.class));
        //получаем ответ и проверяем
        assertThat("hasNext не равен", getblockedContracts.getHasNext(), is(true));
        assertThat("cursor не равен", getblockedContracts.getNextCursor(), is(listOfBlockedId.last()));
        assertThat("items не равен", getblockedContracts.getItems().size(), is(1));
        assertThat("договор не равен", getblockedContracts.getItems().get(0).getId(), is(listOfBlockedId.first()));
    }

    @SneakyThrows
    @Test
    @AllureId("1491752")
    @DisplayName("getBlockedContracts. Передан cursor")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка договоров, на которые наложена техническая блокировка.")
    void C1491752() {
        int limit = 30;
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebel.siebelIdMasterAdmin, investIdMaster, null, contractIdMaster,  ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, new BigDecimal(10.00), "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        //создаем подписку клиента slave на strategy клиента master
        steps.createSubcription(investIdSlave, ClientRiskProfile.aggressive, contractIdSlave,null,
            ContractState.tracked, strategyId,true, SubscriptionStatus.active,
            new java.sql.Timestamp(OffsetDateTime.now().toInstant().getEpochSecond()),null,false);
        //получаем список заблокированных контрактов из БД, которые подписаны на стратегию
        List <Contract> getAllBlockedContracts = contractService.findLimitBlockedContract(true, contractIdSlave).stream()
            .filter(c -> c.getState().equals(ContractState.tracked))
            .collect(Collectors.toList());
        String nextCursor = "";
        SortedSet<String>  listOfBlockedId = new TreeSet<>();
        for (int i = 0; i < getAllBlockedContracts.size(); i++) {
            listOfBlockedId.add(getAllBlockedContracts.get(i).getId());
            if ( getAllBlockedContracts.size() > limit) {
                nextCursor = getAllBlockedContracts.get(limit-1).getId();
            }
            else {
                nextCursor = listOfBlockedId.last();
            }
        }
        //вызываем метод getBlockedContracts
        GetBlockedContractsResponse getblockedContracts = contractApiAdminCreator.get().getBlockedContracts()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking")
            .cursorQuery(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBlockedContractsResponse.class));
        Boolean next = true;
        int contractBlockSize = limit;
        if (listOfBlockedId.size() <= limit) {
            contractBlockSize = listOfBlockedId.size();
        }
        //получаем ответ и проверяем
        assertThat("hasNext не равен", getblockedContracts.getHasNext(), is(next));
        assertThat("cursor не равен", getblockedContracts.getNextCursor(), is(nextCursor));
        assertThat("items не равен", getblockedContracts.getItems().size(), is(contractBlockSize));
    }


    @SneakyThrows
    @Test
    @AllureId("1491596")
    @DisplayName("getBlockedContracts. Не передан limit")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка договоров, на которые наложена техническая блокировка.")
    void C1491596() {
        //получаем список заблокированных контрактов из БД, которые подписаны на стратегию
        List <Contract> getAllBlockedContracts = contractService.findAllBlockedContract(true).stream()
            .filter(c -> c.getState().equals(ContractState.tracked))
            .collect(Collectors.toList());
        SortedSet<String>  listOfBlockedId = new TreeSet<>();
        for (int i = 0; i < defaultLimit; i++) {
            listOfBlockedId.add(getAllBlockedContracts.get(i).getId());
        }
        //вызываем метод getBlockedContracts
        GetBlockedContractsResponse getblockedContracts = contractApiAdminCreator.get().getBlockedContracts()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBlockedContractsResponse.class));
        //получаем ответ и проверяем
        assertThat("hasNext не равен", getblockedContracts.getHasNext(), is(true));
        assertThat("cursor не равен", getblockedContracts.getNextCursor(), is(listOfBlockedId.last()));
        assertThat("items не равен", getblockedContracts.getItems().size(), is(defaultLimit));
        assertThat("договор не равен", getblockedContracts.getItems().get(0).getId(), is(listOfBlockedId.first()));

    }

    public static int randomNumber(int min, int max) {
        int number = min + (int) (Math.random() * max);
        return number;
    }

}
