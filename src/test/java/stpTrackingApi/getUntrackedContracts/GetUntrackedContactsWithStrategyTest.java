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
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.tracking.api.ContractApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.GetUntrackedContractsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.configuration.client.TrackingApiClientAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.time.LocalDateTime;
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
    TrackingDatabaseAutoConfiguration.class,
    TrackingApiClientAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class})
public class GetUntrackedContactsWithStrategyTest {

    @Autowired
    BillingService billingService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    ProfileService profileService;

    ContractApi contractApi = ApiClient.api(ApiClient.Config.apiConfig()).contract();
    Client client;
    Contract contract;
    Strategy strategy;
    Profile profile;
    String SIEBEL_ID = "1-1VAEYWG";

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            trackingService.deleteStrategy(strategy);
            contractService.deleteContract(contract);
            clientService.deleteClient(client);
        });
    }

    @Test
    @AllureId("173619")
    @DisplayName("C173619.GetUntrackedContracts.У siebelId, все договора уже заняты в др статегиях")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает список доступных договоров для подключения стратегии. Валидируем договоры клиента на доступность подключения к автоследованию")
    void C173619() {
        //находим клиента в social и берем данные по профайлу
        profile = profileService.getProfileBySiebleId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //получаем список Брокерских договоров, по SIEBLE_ID
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        //создаем по полученному InvestId запись в tracking.client
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);
        //для каждого брокерского договора создаем записи в БД tracking.contract и tracking.strategy
        for (int i = 0; i < findValidAccountWithSiebleId.size(); i++) {
            UUID strategyId = UUID.randomUUID();
            String contractId = findValidAccountWithSiebleId.get(i).getId();
            createClientWintContractAndStrategyMulti(investId, socialProfile, contractId, null, ContractState.untracked,
                StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.active, 0, LocalDateTime.now());
        }
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
        GetUntrackedContractsResponse expecResponse = contractApi.getUntrackedContracts()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetUntrackedContractsResponse.class));
        //записываем полученные от метода GetUntrackedContracts договора в список и сортируем
        List<String> contractIdsResponse = new ArrayList<>();
        for (int i = 0; i < expecResponse.getItems().size(); i++) {
            contractIdsResponse.add(expecResponse.getItems().get(i).getId());
        }
        contractIdsResponse.sort(String::compareTo);
        //проверяем  договора
        assertThat("номера договоров не равно", contractIdsResponse, is(contractIdsDB));
    }


    @Test
    @AllureId("638985")
    @DisplayName("C638985.GetUntrackedContracts.Получение списка доступных договоров, один из договоров в статусе tracked")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает список доступных договоров для подключения стратегии.")
    void C638985() {
        //находим клиента в social и берем данные по профайлу
        profile = profileService.getProfileBySiebleId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //находим investId клиента в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        String contractId = findValidAccountWithSiebleId.get(0).getId();
        //добавляем 1 договор в автоследование:3 записи: client, contract, strategy
        createClientWintContractAndStrategyMulti(investId, socialProfile, contractId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.active, 0, LocalDateTime.now());
        //оставшиеся брокерские договора записываем в список contractIdsDB
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
        GetUntrackedContractsResponse expecResponse = contractApi.getUntrackedContracts()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetUntrackedContractsResponse.class));
        //записываем полученные от метода GetUntrackedContracts договора в список и сортируем
        List<String> contractIdsResponse = new ArrayList<>();
        for (int i = 0; i < expecResponse.getItems().size(); i++) {
            contractIdsResponse.add(expecResponse.getItems().get(i).getId());
        }
        contractIdsResponse.sort(String::compareTo);
        //проверяем, что метод getUntrackedContracts возвращает, только те, открытие брокерские догорора,
        // которые не заняты в роли мастера в tracking.contract
        assertThat("номера договоров не равно", contractIdsResponse, is(contractIdsDB));
    }

    @Test
    @AllureId("638983")
    @DisplayName("C638983.GetUntrackedContracts.Получение списка доступных договоров, один из договоров в статусе untracked")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает список доступных договоров для подключения стратегии.")
    void C638983() {
        //находим клиента в social и берем данные по профайлу
        profile = profileService.getProfileBySiebleId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //находим investId клиента в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        String contractId = findValidAccountWithSiebleId.get(0).getId();
        //добавляем 1 договор в автоследование:3 записи: client, contract, strategy
        createClientWintContractAndStrategyMulti(investId, socialProfile, contractId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.active, 0, LocalDateTime.now());
        //оставшиеся брокерские договора записываем в список contractIdsDB
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
        GetUntrackedContractsResponse expecResponse = contractApi.getUntrackedContracts()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetUntrackedContractsResponse.class));
        //записываем полученные от метода GetUntrackedContracts договора в список и сортируем
        List<String> contractIdsResponse = new ArrayList<>();
        for (int i = 0; i < expecResponse.getItems().size(); i++) {
            contractIdsResponse.add(expecResponse.getItems().get(i).getId());
        }
        contractIdsResponse.sort(String::compareTo);
        //проверяем, что метод getUntrackedContracts возвращает, только те, открытие брокерские догорора,
        // которые не заняты в роли мастера в tracking.contract
        assertThat("номера договоров не равно", contractIdsResponse, is(contractIdsDB));
    }


    //***методы для работы тестов**************************************************************************
    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWintContractAndStrategyMulti(UUID investId, SocialProfile socialProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                                  StrategyCurrency strategyCurrency, StrategyRiskProfile strategyRiskProfile,
                                                  StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
        UUID strategyId = UUID.randomUUID();
        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contract = contractService.saveContract(contract);
        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contract)
            .setTitle("test strategy 001")
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription("Тестовая стратегия для автотестов")
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(1);
        strategy = trackingService.saveStrategy(strategy);
    }
}
