package stpTrackingApi.getUntrackedContracts;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
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
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.tracking.api.ContractApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.GetUntrackedContractsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.services.database.ContractService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

//import ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient;


@Epic("getUntrackedContracts - Определение списка доступных для стратегии счетов")
@Feature("TAP-6652")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class
})
public class GetUntrackedContactsSuccessTest {
    @Autowired
    BillingService billingService;
    @Autowired
    ContractService сontractService;

    ContractApi contractApi = ApiClient.api(ApiClient.Config.apiConfig()).contract();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.api(
        ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();

    @Test
    @AllureId("173546")
    @DisplayName("C173546.GetUntrackedContracts.Успешное получения списка доступных брокерских договоров")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает список доступных договоров для подключения стратегии")
    void C173546() {
        String SIEBEL_ID = "5-4MMYRD1X";
        //получаем список Брокерских договоров
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID);
        List<String> contractIdsDB = new ArrayList<>();
        //проверяем, что нет записи в tracking.contract  по найденным контрактам и или если его статус untracked
        for (int i = 0; i < findValidAccountWithSiebleId.size(); i++) {
            Optional<Contract> contractOpt = сontractService.findContract(findValidAccountWithSiebleId.get(i).getId());
            if (!contractOpt.isPresent() || contractOpt.get().getState() == ContractState.untracked) {
                contractIdsDB.add(findValidAccountWithSiebleId.get(i).getId());
            }
        }
        //сортируем список полученных договоров
        contractIdsDB.sort(String::compareTo);
        //вызываем метод  GetUntrackedContract
        GetUntrackedContractsResponse expecResponse = contractApi.getUntrackedContracts()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetUntrackedContractsResponse.class));
        //записываем список договоров, который вернул метод GetUntrackedContract
        List<String> contractIdsResponse = new ArrayList<>();
        for (int i = 0; i < expecResponse.getItems().size(); i++) {
            contractIdsResponse.add(expecResponse.getItems().get(i).getId());
        }
       //сортируем договора
        contractIdsResponse.sort(String::compareTo);
        //сравниваем список доступных договоров с тем, что есть в БД, и то, что вернул метод
        assertThat("номера договоров не равно", contractIdsResponse, is(contractIdsDB));
    }

    @Test
    @AllureId("173602")
    @DisplayName("C173602.GetUntrackedContracts.Обработка договоров siebelId, статусом != 'opened'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод возвращает список доступных договоров для подключения стратегии")
    void C173602() {
        String SIEBEL_ID = "5-164JGM7QI";
        //получаем список открытых Брокерских договоров по SIEBEL_ID, записываем  сортируем их
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID);
        List<String> contractIdsDB = new ArrayList<>();
        for (int i = 0; i < findValidAccountWithSiebleId.size(); i++) {
            //проверяем, что контракт не найден в tracking.contract или или если его статус untracked
            Optional<Contract> contractOpt = сontractService.findContract(findValidAccountWithSiebleId.get(i).getId());
            if (!contractOpt.isPresent() || contractOpt.get().getState() == ContractState.untracked) {
                contractIdsDB.add(findValidAccountWithSiebleId.get(i).getId());
            }
        }
        contractIdsDB.sort(String::compareTo);
        //вызываем метод  GetUntrackedContracts с siebleId
        GetUntrackedContractsResponse expecResponse = contractApi.getUntrackedContracts()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetUntrackedContractsResponse.class));
        //записываем список договоров, который вернул метод GetUntrackedContract
        List<String> contractIdsResponse = new ArrayList<>();
        for (int i = 0; i < expecResponse.getItems().size(); i++) {
            contractIdsResponse.add(expecResponse.getItems().get(i).getId());
        }
        contractIdsResponse.sort(String::compareTo);
        ///проверяем, что договор со статусом != opened не попал в список возращаемым методом getUntrackedContracts
        assertThat("номера договоров не равно", contractIdsResponse, is(contractIdsDB));
    }



    @Test
    @AllureId("173590")
    @DisplayName("C173590.GetUntrackedContracts.Обработка договоров с типом ИИС или Копилка (brokerType != broker)")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод возвращает список доступных договоров для подключения стратегии")
    void C173590() {
        String SIEBEL_ID = "5-YE3B7BWM";
        //получаем список открытых Брокерских договоров, записываем  сортируем их
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID);
        List<String> contractIdsDB = new ArrayList<>();
        for (int i = 0; i < findValidAccountWithSiebleId.size(); i++) {
            //проверяем, что контракт не найден в tracking.contract или если  его статус untracked
            Optional<Contract> contractOpt = сontractService.findContract(findValidAccountWithSiebleId.get(i).getId());
            if (!contractOpt.isPresent() || contractOpt.get().getState() == ContractState.untracked) {
                contractIdsDB.add(findValidAccountWithSiebleId.get(i).getId());
            }
        }
        contractIdsDB.sort(String::compareTo);
        //вызываем метод  GetUntrackedContracts с siebleId
        GetUntrackedContractsResponse expecResponse = contractApi.getUntrackedContracts()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetUntrackedContractsResponse.class));
        //записываем список договоров, который вернул метод GetUntrackedContract
        List<String> contractIdsResponse = new ArrayList<>();
        for (int i = 0; i < expecResponse.getItems().size(); i++) {
            contractIdsResponse.add(expecResponse.getItems().get(i).getId());
        }
        contractIdsResponse.sort(String::compareTo);
        //проверяем, что договор с типом != broker не попал в список возращаемым методом getUntrackedContracts
        assertThat("номера договоров не равно", contractIdsResponse, is(contractIdsDB));
    }
}
