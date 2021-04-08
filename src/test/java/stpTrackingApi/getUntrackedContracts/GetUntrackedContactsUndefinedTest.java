package stpTrackingApi.getUntrackedContracts;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.swagger.tracking.api.ContractApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.GetUntrackedContractsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.entities.enums.ContractRole;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Epic("getUntrackedContracts - Определение списка доступных для стратегии счетов")
@Feature("TAP-6652")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class})
public class GetUntrackedContactsUndefinedTest {
    @Autowired
    BillingService billingService;
    @Autowired
    ContractService contractService;
    @Autowired
    ClientService clientService;

    BrokerAccount validBrokerAccount;
    ContractApi contractApi = ApiClient.api(ApiClient.Config.apiConfig()).contract();
    // test data
    Client client;
    Contract contract;

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            if (contract != null) {
                contractService.deleteContract(contract);
            }
            if (client != null) {
                clientService.deleteClient(client);
            }
        });
    }

    @Test
    @AllureId("244867")
    @DisplayName("C244867.GetUntrackedContracts.Договор отключен от стратегии")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод возвращает список доступных договоров для подключения стратегии")
    void C244867() {
       String SIEBLE_ID = "5-4PT2NXOL";
        //получаем список Брокерских договоров
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBLE_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        //получаем идентификатор догорова
        String contractId = findValidAccountWithSiebleId.get(0).getId();
        //создаем клиета в БД автоследования в tracking.client и tracking.contract в статусе untracked и ролью undefined
        createClientWintContract(investId, null, contractId, null, ContractState.untracked);
       //отфильтровываем список договоров клиентов
        List<String> contractIdsDB = new ArrayList<>();
        for (int i = 0; i < findValidAccountWithSiebleId.size(); i++) {
            //проверяем, что контракт не найден в tracking.contract или если его статус untracked
            Optional<Contract> contractOpt = contractService.findContract(findValidAccountWithSiebleId.get(i).getId());
            if (!contractOpt.isPresent() || contractOpt.get().getState() == ContractState.untracked) {
                contractIdsDB.add(findValidAccountWithSiebleId.get(i).getId());
            }
        }
        contractIdsDB.sort(String::compareTo);
        //вызываем метод  GetUntrackedContracts с siebleId
        ru.qa.tinkoff.swagger.tracking.model.GetUntrackedContractsResponse expecResponse = contractApi.getUntrackedContracts()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBLE_ID)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetUntrackedContractsResponse.class));
        //записываем список договоров, который вернул метод GetUntrackedContract
        List<String> contractIdsResponse = new ArrayList<>();
        for (int i = 0; i < expecResponse.getItems().size(); i++) {
            contractIdsResponse.add(expecResponse.getItems().get(i).getId());
        }
        contractIdsResponse.sort(String::compareTo);
        assertThat("номера договоров не равно", contractIdsResponse, is(contractIdsDB));
    }

//***методы для работы тестов***********************************************************************

//метод создает клиента и договор в БД автоследование: tracking.client, tracking.contract
    void createClientWintContract(UUID investId, SocialProfile socialProfile, String contractId,
                                  ContractRole contractRole, ContractState contractState) {
        UUID strategyId = null;
        validBrokerAccount = billingService.getFirstValid();
        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contract = contractService.saveContract(contract);
    }
}