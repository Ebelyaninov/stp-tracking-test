package stpTrackingApi.deleteSubscription;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.tracking.steps.StpTrackingApiSteps;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


@Epic("deleteSubscription - Удаление подписки на торговую стратегию")
@Feature("TAP-7383")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class
})


public class DeleteSubscriptionErrorTest {
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StpTrackingApiSteps steps;
    SubscriptionApi subscriptionApi = ApiClient.api(ApiClient.Config.apiConfig()).subscription();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    Client clientMaster;
    Contract contractMaster;
    Strategy strategyMaster;
    Client clientSlave;
    Contract contractSlave;
    Subscription subscription;
    String siebelIdMaster = "1-2X5IYXJ";
    String siebelIdSlave = "5-12UL9UV9C";

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
                clientSlave = clientService.getClient(clientSlave.getId());
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientSlave);
            } catch (Exception e) {
            }
            try {
                trackingService.deleteStrategy(strategyMaster);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractMaster);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientMaster);
            } catch (Exception e) {
            }
        });
    }


    private static Stream<Arguments> provideRequiredParamDeleteSubscription() {
        return Stream.of(
            Arguments.of(null, "4.5.6", "android", "2010104269"),
            Arguments.of("trading-invest", null, "android", "2010104269"),
            Arguments.of("trading-invest", "4.5.6", null, "2010104269")
        );
    }


    @ParameterizedTest
    @MethodSource("provideRequiredParamDeleteSubscription")
    @AllureId("535364")
    @DisplayName("C535364.DeleteSubscription.Валидация обязательных параметров: x-app-name, x-app-version, x-platform, strategyId")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод удаления подписки на торговую стратегию ведомым.")
    void C535364(String name, String version, String platform, String contract) {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        String contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdSlave)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        String contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //вызываем метод CreateSubscription
        subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(1));
        int count = strategyMaster.getSlavesCount();
        SubscriptionApi.DeleteSubscriptionOper deleteSubscription = subscriptionApi.deleteSubscription()
            .xTcsSiebelIdHeader(siebelIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            deleteSubscription = deleteSubscription.xAppNameHeader(name);
        }
        if (version != null) {
            deleteSubscription = deleteSubscription.xAppVersionHeader(version);
        }
        if (platform != null) {
            deleteSubscription = deleteSubscription.xPlatformHeader(platform);
        }
        if (contract != null) {
            deleteSubscription = deleteSubscription.contractIdQuery(contract);
        }
        deleteSubscription.execute(ResponseBodyData::asString);
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        subscription = subscriptionService.findSubscriptionByContractIdAndStatus(contract, SubscriptionStatus.active);
        contractSlave = contractService.getContract(contract);
        clientSlave = clientService.getClient(investIdSlave);
        checkParam(count, strategyId);
    }


    @Test
    @AllureId("535364")
    @DisplayName("C535364.DeleteSubscription.Валидация обязательных параметров: contractId")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод удаления подписки на торговую стратегию ведомым.")
    void C535364_1()  {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        String contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdSlave)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        String contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //вызываем метод CreateSubscription
        subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(1));
        int count = strategyMaster.getSlavesCount();
        SubscriptionApi.DeleteSubscriptionOper deleteSubscription = subscriptionApi.deleteSubscription()
            .xTcsSiebelIdHeader(siebelIdSlave)
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (contractIdSlave != null) {
            deleteSubscription = deleteSubscription.contractIdQuery(contractIdSlave);
        }
        deleteSubscription.execute(ResponseBodyData::asString);
        strategyMaster = strategyService.getStrategy(strategyId);
        subscription = subscriptionService.findSubscriptionByContractIdAndStatus(contractIdSlave, SubscriptionStatus.active);
        contractSlave = contractService.getContract(contractIdSlave);
        clientSlave = clientService.getClient(investIdSlave);
        checkParam(count, strategyId);
    }


    @Test
    @AllureId("535365")
    @DisplayName("C535365.DeleteSubscription.Валидация обязательных параметров: x-tcs-siebel-id")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод удаления подписки на торговую стратегию ведомым.")
    void C535365() {
        //находим в активных подписках договор и стратегию
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        String contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdSlave)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        String contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //вызываем метод CreateSubscription
        subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(1));
        int count = strategyMaster.getSlavesCount();
        //вызываем метод удаления подписки без siebleId
        SubscriptionApi.DeleteSubscriptionOper deleteSubscription = subscriptionApi.deleteSubscription()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(401));
        deleteSubscription.execute(ResponseBodyData::asString);

        strategyMaster = strategyService.getStrategy(strategyId);
        subscription = subscriptionService.findSubscriptionByContractIdAndStatus(contractIdSlave, SubscriptionStatus.active);
        contractSlave = contractService.getContract(contractIdSlave);
        clientSlave = clientService.getClient(investIdSlave);
        checkParam(count, strategyId);
    }

    private static Stream<Arguments> provideParamSiebleDeleteSubscription() {
        return Stream.of(
            Arguments.of(""),
            Arguments.of("5-1DJ2D8IIQ12")
        );
    }

    @ParameterizedTest
    @MethodSource("provideParamSiebleDeleteSubscription")
    @AllureId("535366")
    @DisplayName("C535366.DeleteSubscription.Валидация x-tcs-siebel-id: < 1 символа, > 12 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод удаления подписки на торговую стратегию ведомым.")
    void C535366(String siebel) {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        String contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdSlave)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        String contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //вызываем метод CreateSubscription
        subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(1));
        int count = strategyMaster.getSlavesCount();
        //вызываем метод удаления подписки
        subscriptionApi.deleteSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebel)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем, что количество подписок на стратегию не изменилось
        strategyMaster = strategyService.getStrategy(strategyId);
        subscription = subscriptionService.findSubscriptionByContractIdAndStatus(contractIdSlave, SubscriptionStatus.active);
        contractSlave = contractService.getContract(contractIdSlave);
        clientSlave = clientService.getClient(investIdSlave);
        checkParam(count, strategyId);
    }


    private static Stream<Arguments> provideParamContractDeleteSubscription() {
        return Stream.of(
            Arguments.of(""),
            Arguments.of("20281258431")
        );
    }

    @ParameterizedTest
    @MethodSource("provideParamContractDeleteSubscription")
    @AllureId("535368")
    @DisplayName("C535368.DeleteSubscription.Валидация contractId: < 1 символа, > 10 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод удаления подписки на торговую стратегию ведомым.")
    void C535368(String contractNew) {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        String contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdSlave)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        String contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //вызываем метод CreateSubscription
        subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(1));
        int count = strategyMaster.getSlavesCount();
        //вызываем метод удаления подписки
        subscriptionApi.deleteSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery(contractNew)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(ResponseBodyData::asString);
        //проверяем, что количество подписок на стратегию не изменилось
        strategyMaster = strategyService.getStrategy(strategyId);
        subscription = subscriptionService.findSubscriptionByContractIdAndStatus(contractIdSlave, SubscriptionStatus.active);
        contractSlave = contractService.getContract(contractIdSlave);
        clientSlave = clientService.getClient(investIdSlave);
        checkParam(count, strategyId);
    }


    @Test
    @AllureId("535370")
    @DisplayName("C535370.DeleteSubscription.Не существующие значение x-tcs-siebel-id")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод удаления подписки на торговую стратегию ведомым.")
    void C535370() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        String contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdSlave)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        String contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //вызываем метод CreateSubscription
        subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(1));
        int count = strategyMaster.getSlavesCount();
        //вызываем метод удаления подписки
        subscriptionApi.deleteSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader("5-1DJ2D8555")
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(ResponseBodyData::asString);
        //проверяем, что количество подписок на стратегию не изменилось
        strategyMaster = strategyService.getStrategy(strategyId);
        subscription = subscriptionService.findSubscriptionByContractIdAndStatus(contractIdSlave, SubscriptionStatus.active);
        contractSlave = contractService.getContract(contractIdSlave);
        clientSlave = clientService.getClient(investIdSlave);
        checkParam(count, strategyId);
    }


    @Test
    @AllureId("535371")
    @DisplayName("C535371.DeleteSubscription.Не существующие значение contractId")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод удаления подписки на торговую стратегию ведомым.")
    void C535371() {
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        String contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdSlave)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        String contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //вызываем метод CreateSubscription
        subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(1));
        int count = strategyMaster.getSlavesCount();
        //вызываем метод удаления подписки
        subscriptionApi.deleteSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery("3000348224")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(ResponseBodyData::asString);
        //проверяем, что количество подписок на стратегию не изменилось
        strategyMaster = strategyService.getStrategy(strategyId);
        subscription = subscriptionService.findSubscriptionByContractIdAndStatus(contractIdSlave, SubscriptionStatus.active);
        contractSlave = contractService.getContract(contractIdSlave);
        clientSlave = clientService.getClient(investIdSlave);
        checkParam(count, strategyId);
    }


    @Test
    @AllureId("535410")
    @DisplayName("C535410.DeleteSubscription.Договор не соответствует siebleId")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод удаления подписки на торговую стратегию ведомым.")
    void C535410() {
        String contractIdOther = "2020038119";
        String title = "тест стратегия autotest";
        String description = "new test стратегия autotest";
        UUID strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdMaster)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdMaster = resAccountMaster.getInvestId();
        String contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelIdSlave)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlave = resAccountSlave.getInvestId();
        String contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //вызываем метод CreateSubscription
        subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(1));
        int count = strategyMaster.getSlavesCount();
        subscriptionApi.deleteSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .contractIdQuery(contractIdOther)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(ResponseBodyData::asString);
        //проверяем, что количество подписок на стратегию не изменилось
        strategyMaster = strategyService.getStrategy(strategyId);
        subscription = subscriptionService.findSubscriptionByContractIdAndStatus(contractIdSlave, SubscriptionStatus.active);
        contractSlave = contractService.getContract(contractIdSlave);
        clientSlave = clientService.getClient(investIdSlave);
        checkParam(count, strategyId);
    }


    //***методы для работы тестов**************************************************************************
//    //метод создает клиента, договор и стратегию в БД автоследования
//    void createClientWintContractAndStrategy(String SIEBEL_ID, UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
//                                             UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
//                                             ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
//                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
//        //создаем запись о клиенте в tracking.client
//        clientMaster = clientService.createClient(investId, ClientStatusType.registered, null);
//        // создаем запись о договоре клиента в tracking.contract
//        contractMaster = new Contract()
//            .setId(contractId)
//            .setClientId(clientMaster.getId())
//            .setRole(contractRole)
//            .setState(contractState)
//            .setStrategyId(null)
//            .setBlocked(false);
//        contractMaster = contractService.saveContract(contractMaster);
//        //создаем запись о стратегии клиента
//        strategyMaster = new Strategy()
//            .setId(strategyId)
//            .setContract(contractMaster)
//            .setTitle(title)
//            .setBaseCurrency(strategyCurrency)
//            .setRiskProfile(strategyRiskProfile)
//            .setDescription(description)
//            .setStatus(strategyStatus)
//            .setSlavesCount(slaveCount)
//            .setActivationTime(date)
//            .setScore(1);
//        strategyMaster = trackingService.saveStrategy(strategyMaster);
//    }


    void checkParam(int count, UUID strategyId) {
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(count));
        //находим подписку и проверяем по ней данные
        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("active"));
        assertThat("время удаления подписки не равно", subscription.getEndTime(), is(IsNull.nullValue()));
        //находим запись по контракту ведомого и проверяем значения
        assertThat("Роль ведомого не равна null", contractSlave.getRole(), is(nullValue()));
        assertThat("статус ведомого не равен", contractSlave.getState().toString(), is("tracked"));
        assertThat("стратегия у ведомого не равна", contractSlave.getStrategyId(), is(strategyId));
        assertThat("номера клиента не равно", clientSlave.getMasterStatus().toString(), is("none"));
    }
}

