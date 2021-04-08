import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.configuration.client.TrackingApiClientAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.enums.ContractRole;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.util.UUID;

import static io.qameta.allure.Allure.step;


@Epic("getUntrackedContracts - Определение списка доступных для стратегии счетов")
@Feature("TAP-6652")
@ExtendWith(AllureJunit5.class)
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class, TrackingApiClientAutoConfiguration.class})
public class GetUntrackedContacts {

//    @Autowired
//    TrackingApiService trackingApiService;

    @Autowired
    BillingService billingService;

    @Autowired
    TrackingService trackingService;

    BrokerAccount validBrokerAccount;
    Client createdClient;

    @BeforeAll
    void createClient() {
        step("Получаем валидный брокерский аккаунт и создаем клиента автоследования", () -> {
            validBrokerAccount = billingService.getFirstValid();
         //   createdClient = trackingService.createClient();
        });
    }

    @AfterAll
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            trackingService.deleteClient(createdClient);
        });
    }

//    @Test
    @AllureId("173619")
    @DisplayName("Нет доступных договоров для стратегии: у siebelId, все договора уже заняты в др статегиях")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод возвращает список доступных договоров для подключения стратегии. Валидируем договоры клиента на доступность подключения к автоследованию")
    void checkNoAvailableContractsBehaviour() {
        createdClient.getContracts().add(new Contract()
            .setId(validBrokerAccount.getId())
            .setClientId(createdClient.getId())
            .setRole(ContractRole.master)
            .setStrategyId(UUID.fromString("f525f07f-b203-45ba-b86d-55d31e1780a5"))
            .setState(ContractState.tracked));
        trackingService.saveClient(createdClient);

//        GetUntrackedContractsResponse response = trackingApiService.getUntrackedContracts(
//            TrackingApiService.GetUntrackedContactsRequestBuilder
//                .builder()
//                .siebelId(validBrokerAccount.getInvestAccount().getSiebelId())
//                .build()
//        );
//
//        assertThat("Items list not empty", response.getItems(), is(empty()));
    }
}
