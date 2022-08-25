package stpTrackingAdminApi.deleteSubscriptions;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.ApiAdminCreator;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("deleteSubscription - Инициация удаления всех активных подписок у торговой")
@Feature("ISTAP-5432")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("deleteSubscription")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class
})


public class deleteSubscriptionsErrorTest {
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ApiAdminCreator<StrategyApi> subscriptionAdminCreator;

    Strategy strategyMaster;
    Subscription subscription;
    String siebelIdMaster;
    String siebelIdSlave;
    String description = "стратегия autotest DeleteSubscription";
    String contractIdMaster;
    UUID investIdMaster;
    UUID investIdSlave;
    UUID strategyId;
    String contractIdSlave;
    String xApiKey = "x-api-key";
    String key = "tracking";

    @BeforeAll
    void getDataForTests() {
        siebelIdMaster = stpSiebel.siebelIdApiMaster;
        siebelIdSlave = stpSiebel.siebelIdApiSlave;
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();

        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        steps.createEventInTrackingContractEvent(contractIdMaster);
        steps.createEventInTrackingContractEvent(contractIdSlave);
        steps.deleteDataFromDb(siebelIdMaster);
        steps.deleteDataFromDb(siebelIdSlave);
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdSlave));
            } catch (Exception e) {
            }
            try {
                strategyService.deleteStrategy(strategyService.getStrategy(strategyId));
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
                contractService.deleteContract(contractService.getContract(contractIdMaster));
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


    @Test
    @AllureId("1903443")
    @DisplayName("C1903443.DeleteSubscription.Отписка ведомых от стратегии со status != 'frozen'\n")
    @Subfeature("Успешные сценарии")
    @Description("Метод для инициации удаления всех активных подписок у торговой стратегии.")
    void C1903443() throws Exception {
        LocalDateTime time = LocalDateTime.now().withNano(0);
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.conservative,
            StrategyStatus.active, 1, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11", true, true, null);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.conservative, contractIdSlave, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        // Отправляем команду на отписку
        StrategyApi.DeleteSubscriptionsOper deleteSubscription = subscriptionAdminCreator.get().deleteSubscriptions()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader(siebelIdSlave)
            .respSpec(spec -> spec.expectStatusCode(422));
        if (contractIdSlave != null) {
            deleteSubscription = deleteSubscription.strategyIdPath(strategyId);
        }
        deleteSubscription.execute(ResponseBodyData::asString);

        deleteSubscription.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(deleteSubscription.execute(ResponseBodyData::asString));

        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("0344-19-B18"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Не найдена стратегия в strategy или не подходит под условия"));
    }

    @Test
    @AllureId("1903458")
    @DisplayName("C1903458.DeleteSubscription.Не удалось найти стратегию с нужными параметрами в таблице strategy\n")
    @Subfeature("Успешные сценарии")
    @Description("Метод для инициации удаления всех активных подписок у торговой стратегии.")
    void C1903458() throws Exception {
        // Отправляем команду на отписку
        StrategyApi.DeleteSubscriptionsOper deleteSubscription = subscriptionAdminCreator.get().deleteSubscriptions()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader(siebelIdSlave)
            .respSpec(spec -> spec.expectStatusCode(422));
        if (contractIdSlave != null) {
            deleteSubscription = deleteSubscription.strategyIdPath("0b000000-0eee-0db0-b0b0-00bc0cec000b");
        }
        deleteSubscription.execute(ResponseBodyData::asString);

        deleteSubscription.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(deleteSubscription.execute(ResponseBodyData::asString));

        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("0344-19-B01"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Стратегия не найдена"));
    }


    @Test
    @AllureId("1903453")
    @DisplayName("C1903453.DeleteSubscription.Отписка ведомых от стратегии со статусом подписки status != 'active'")
    @Subfeature("Успешные сценарии")
    @Description("Метод для инициации удаления всех активных подписок у торговой стратегии.")
    void C1903453() throws Exception {
        LocalDateTime time = LocalDateTime.now().withNano(0);
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.conservative,
            StrategyStatus.frozen, 1, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11", true, true, null);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.conservative, contractIdSlave, ContractState.tracked,
            strategyId, SubscriptionStatus.draft, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        // Отправляем команду на отписку
        subscriptionAdminCreator.get().deleteSubscriptions()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader(siebelIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(202))
            .execute(ResponseBodyData::asString);

        //находим подписку и проверяем по ней данные
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("draft"));
        strategyMaster = strategyService.getStrategy(strategyId);

    }

    @Test
    @AllureId("1903051")
    @DisplayName("C1903051.DeleteSubscription.Заголовок X-API-KEY не передан")
    @Subfeature("Успешные сценарии")
    @Description("Метод для инициации удаления всех активных подписок у торговой стратегии.")
    void C1903051() throws Exception {
        LocalDateTime time = LocalDateTime.now().withNano(0);
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.conservative,
            StrategyStatus.frozen, 1, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11", true, true, null);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.conservative, contractIdSlave, ContractState.tracked,
            strategyId, SubscriptionStatus.draft, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        // Отправляем команду на отписку
        subscriptionAdminCreator.get().deleteSubscriptions()
            .xAppNameHeader("invest")
            .xTcsLoginHeader(siebelIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(ResponseBodyData::asString);
    }

    @Test
    @AllureId("1903440")
    @DisplayName("C1903440.DeleteSubscription.Среди входных параметров передано некорректное значение")
    @Subfeature("Успешные сценарии")
    @Description("Метод для инициации удаления всех активных подписок у торговой стратегии.")
    void C1903440() throws Exception {
        LocalDateTime time = LocalDateTime.now().withNano(0);
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, ClientRiskProfile.conservative, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, StrategyRiskProfile.conservative,
            StrategyStatus.frozen, 1, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11", true, true, null);
        //создаем подписку для slave
        OffsetDateTime startSubTime = OffsetDateTime.now();
        steps.createSubcription(investIdSlave, ClientRiskProfile.conservative, contractIdSlave, ContractState.tracked,
            strategyId, SubscriptionStatus.active, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        // Отправляем команду на отписку
        StrategyApi.DeleteSubscriptionsOper deleteSubscription = subscriptionAdminCreator.get().deleteSubscriptions()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("))))")
            .respSpec(spec -> spec.expectStatusCode(400));
        if (contractIdSlave != null) {
            deleteSubscription = deleteSubscription.strategyIdPath("!!!");
        }
        deleteSubscription.execute(ResponseBodyData::asString);

        deleteSubscription.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(deleteSubscription.execute(ResponseBodyData::asString));

        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("0344-00-Z99"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

}

