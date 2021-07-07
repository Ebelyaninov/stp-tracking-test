package stpTrackingApi.createSubscription;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
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
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Epic("createSubscription - Создание подписки на торговую стратегию")
@Feature("TAP-7383")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class
})

public class CreateSubscriptionErrorTest {
    @Autowired
    BillingService billingService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;

    SubscriptionApi subscriptionApi= ApiClient.api(ApiClient.Config.apiConfig()).subscription();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    Profile profile;
    Client clientMaster;
    Contract contractMaster;
    Strategy strategyMaster;
    String siebelIdMaster = "1-5RLRHAS";
    String siebelIdSlave = "5-11PX4UETD";
    String siebelIdSlaveNotBrokerOpen = "5-164JGM7QI";
    String siebelIdSlaveNotBroker = "5-YE3B7BWM";

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                trackingService.deleteStrategy(strategyMaster);
            } catch (Exception e) {}
            try {
                contractService.deleteContract(contractMaster);
            } catch (Exception e) {}
            try {
                clientService.deleteClient(clientMaster);
            } catch (Exception e) {}
        });
    }


    private static Stream<Arguments> provideRequiredParamCreateSubscription() {
        return Stream.of(
            Arguments.of(null, "4.5.6", "android", "2000339404"),
            Arguments.of("trading-invest", null, "android", "2000339404"),
            Arguments.of("trading-invest", "4.5.6", null, "2000339404"),
            Arguments.of("trading-invest", "4.5.6", "android", null)

        );
    }

    @ParameterizedTest
    @MethodSource("provideRequiredParamCreateSubscription")
    @AllureId("534131")
    @DisplayName("C534131.CreateSubscription.Валидация обязательных парамертов: x-app-name, x-app-version, x-platform, x-tcs-siebel-id, contractId, strategyId")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания подписки на торговую стратегию ведомым.")
    void C534131(String name, String version, String platform, String contract) throws Exception {
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
        LocalDateTime dateCreateTr = null;
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        SubscriptionApi.CreateSubscriptionOper createSubscription = subscriptionApi.createSubscription()
            .xTcsSiebelIdHeader(siebelIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            createSubscription = createSubscription.xAppNameHeader(name);
        }
        if (version != null) {
            createSubscription = createSubscription.xAppVersionHeader(version);
        }
        if (platform != null) {
            createSubscription = createSubscription.xPlatformHeader(platform);
        }
        if (contract != null) {
            createSubscription = createSubscription.contractIdQuery(contract);
        }
        createSubscription.execute(ResponseBodyData::asString);
        Optional<Client> clientOpt = clientService.findClient(investIdSlave);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = contractService.findContract(contractIdSlave);
        assertThat("запись по контракту не равно", contractOpt.isPresent(), is(false));
        Optional<Subscription> subscriptionOpt = subscriptionService.findSubcription(contractIdSlave);
        assertThat("запись по контракту не равно", subscriptionOpt.isPresent(), is(false));
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(0));
    }


    @Test
    @AllureId("535054")
    @DisplayName("535054.CreateSubscription.Валидация обязательных параметров: x-tcs-siebel-id")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания подписки на торговую стратегию ведомым.")
    void C535054() {
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
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        SubscriptionApi.CreateSubscriptionOper createSubscription = subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .xTcsSiebelIdHeader(siebelIdSlave)
            .respSpec(spec -> spec.expectStatusCode(401));
        Optional<Client> clientOpt = clientService.findClient(investIdSlave);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = contractService.findContract(contractIdSlave);
        assertThat("запись по контракту не равно", contractOpt.isPresent(), is(false));
        Optional<Subscription> subscriptionOpt = subscriptionService.findSubcription(contractIdSlave);
        assertThat("запись по контракту не равно", subscriptionOpt.isPresent(), is(false));
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(0));
    }

    private static Stream<Arguments> provideParamSiebleCreateSubscription() {
        return Stream.of(
            Arguments.of(""),
            Arguments.of("5-3FRZQV8J12")
        );
    }

    @ParameterizedTest
    @MethodSource("provideParamSiebleCreateSubscription")
    @AllureId("534145")
    @DisplayName("C534145.CreateSubscription.Валидация обязательных параметров: x-tcs-siebel-id < 1 символа, > 12 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания подписки на торговую стратегию ведомым.")
    void C534145(String sieble) {
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
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        SubscriptionApi.CreateSubscriptionOper createSubscription = subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .xTcsSiebelIdHeader(sieble)
            .respSpec(spec -> spec.expectStatusCode(400));
        Optional<Client> clientOpt = clientService.findClient(investIdSlave);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = contractService.findContract(contractIdSlave);
        assertThat("запись по контракту не равно", contractOpt.isPresent(), is(false));
        Optional<Subscription> subscriptionOpt = subscriptionService.findSubcription(contractIdSlave);
        assertThat("запись по контракту не равно", subscriptionOpt.isPresent(), is(false));
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(0));
    }


    private static Stream<Arguments> provideParamContractCreateSubscription() {
        return Stream.of(
            Arguments.of(""),
            Arguments.of("20243752771")
        );
    }

    @ParameterizedTest
    @MethodSource("provideParamContractCreateSubscription")
    @AllureId("534148")
    @DisplayName("C534148.CreateSubscription.Валидация обязательных параметров: contractId < 1 символа, > 10 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания подписки на торговую стратегию ведомым.")
    void C534148(String contract) {
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
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        SubscriptionApi.CreateSubscriptionOper createSubscription = subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .contractIdQuery(contract)
            .strategyIdPath(strategyId)
            .xTcsSiebelIdHeader(siebelIdSlave)
            .respSpec(spec -> spec.expectStatusCode(400));
        Optional<Client> clientOpt = clientService.findClient(investIdSlave);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = contractService.findContract(contractIdSlave);
        assertThat("запись по контракту не равно", contractOpt.isPresent(), is(false));
        Optional<Subscription> subscriptionOpt = subscriptionService.findSubcription(contractIdSlave);
        assertThat("запись по контракту не равно", subscriptionOpt.isPresent(), is(false));
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(0));
    }


    @Test
    @AllureId("534164")
    @DisplayName("C534164.CreateSubscription.Не существующие значение x-tcs-siebel-id")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания подписки на торговую стратегию ведомым.")
    void C534164() {
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
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .xTcsSiebelIdHeader(siebelIdSlave)
            .respSpec(spec -> spec.expectStatusCode(422));
        Optional<Client> clientOpt = clientService.findClient(investIdSlave);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = contractService.findContract(contractIdSlave);
        assertThat("запись по контракту не равно", contractOpt.isPresent(), is(false));
        Optional<Subscription> subscriptionOpt = subscriptionService.findSubcription(contractIdSlave);
        assertThat("запись по контракту не равно", subscriptionOpt.isPresent(), is(false));
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(0));
    }


    @Test
    @AllureId("534170")
    @DisplayName("C534170.CreateSubscription.Тип Договора клиента != 'broker'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания подписки на торговую стратегию ведомым.")
    void C534170() {
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
            .siebelIdPath(siebelIdSlaveNotBroker)
            .brokerTypeQuery("invest-box")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlaveNotBroker = resAccountSlave.getInvestId();
        String contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        SubscriptionApi.CreateSubscriptionOper createSubscription = subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .xTcsSiebelIdHeader(investIdSlaveNotBroker.toString())
            .respSpec(spec -> spec.expectStatusCode(422));
        Optional<Client> clientOpt = clientService.findClient(investIdSlaveNotBroker);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = contractService.findContract(contractIdSlave);
        assertThat("запись по контракту не равно", contractOpt.isPresent(), is(false));
        Optional<Subscription> subscriptionOpt = subscriptionService.findSubcription(contractIdSlave);
        assertThat("запись по контракту не равно", subscriptionOpt.isPresent(), is(false));
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(0));
    }

    @Test
    @AllureId("534175")
    @DisplayName("C534175.CreateSubscription.Статус договора != 'opened'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания подписки на торговую стратегию ведомым.")
    void C534175() {
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
            .siebelIdPath(siebelIdSlaveNotBroker)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("closed")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdSlaveNotBrokerOpen = resAccountSlave.getInvestId();
        String contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        SubscriptionApi.CreateSubscriptionOper createSubscription = subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .xTcsSiebelIdHeader(siebelIdSlaveNotBrokerOpen)
            .respSpec(spec -> spec.expectStatusCode(422));
        Optional<Client> clientOpt = clientService.findClient(investIdSlaveNotBrokerOpen);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = contractService.findContract(contractIdSlave);
        assertThat("запись по контракту не равно", contractOpt.isPresent(), is(false));
        Optional<Subscription> subscriptionOpt = subscriptionService.findSubcription(contractIdSlave);
        assertThat("запись по контракту не равно", subscriptionOpt.isPresent(), is(false));
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(0));
    }



    @Test
    @AllureId("534183")
    @DisplayName("C534183.CreateSubscription.Не существующие значение contractId")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания подписки на торговую стратегию ведомым.")
    void C534183() {
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
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        SubscriptionApi.CreateSubscriptionOper createSubscription = subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .contractIdQuery("2031912374")
            .strategyIdPath(strategyId)
            .xTcsSiebelIdHeader(siebelIdSlave)
            .respSpec(spec -> spec.expectStatusCode(422));
        Optional<Client> clientOpt = clientService.findClient(investIdSlave);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = contractService.findContract(contractIdSlave);
        assertThat("запись по контракту не равно", contractOpt.isPresent(), is(false));
        Optional<Subscription> subscriptionOpt = subscriptionService.findSubcription(contractIdSlave);
        assertThat("запись по контракту не равно", subscriptionOpt.isPresent(), is(false));
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(0));
    }


    @Test
    @AllureId("534191")
    @DisplayName("C534191.CreateSubscription.Не существующие значение strategyId")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания подписки на торговую стратегию ведомым.")
    void C534191() {
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
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        SubscriptionApi.CreateSubscriptionOper createSubscription = subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .contractIdQuery(contractIdSlave)
            .xTcsSiebelIdHeader(siebelIdSlave)
            .strategyIdPath(UUID.fromString("88888f88-cd5e-4bb6-82be-d46e72886d88"))
            .respSpec(spec -> spec.expectStatusCode(422));
        Optional<Client> clientOpt = clientService.findClient(investIdSlave);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = contractService.findContract(contractIdSlave);
        assertThat("запись по контракту не равно", contractOpt.isPresent(), is(false));
        Optional<Subscription> subscriptionOpt = subscriptionService.findSubcription(contractIdSlave);
        assertThat("запись по контракту не равно", subscriptionOpt.isPresent(), is(false));
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(0));
    }


    @Test
    @AllureId("534302")
    @DisplayName("C534302.CreateSubscription.Создание подписки на неактивную стратегию")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания подписки на торговую стратегию ведомым.")
    void C534302() {
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
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null);
        SubscriptionApi.CreateSubscriptionOper createSubscription = subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .contractIdQuery(contractIdSlave)
            .xTcsSiebelIdHeader(siebelIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(422));
        Optional<Client> clientOpt = clientService.findClient(investIdSlave);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
        Optional<Contract> contractOpt = contractService.findContract(contractIdSlave);
        assertThat("запись по контракту не равно", contractOpt.isPresent(), is(false));
        Optional<Subscription> subscriptionOpt = subscriptionService.findSubcription(contractIdSlave);
        assertThat("запись по контракту не равно", subscriptionOpt.isPresent(), is(false));
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(0));
    }

    @Test
    @AllureId("639163")
    @DisplayName("C639163.CreateSubscription.Создание подписки на тот же договор, что и стратегия")
    @Subfeature("Успешные сценарии")
    @Description("Метод создания подписки на торговую стратегию ведомым.")
    void C639163()  {
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
        //создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now());
        //вызываем метод CreateSubscription
        subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .contractIdQuery(contractIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(ResponseBodyData::asString);
        Optional<Subscription> subscriptionOpt = subscriptionService.findSubcription(siebelIdMaster);
        assertThat("запись по контракту не равно", subscriptionOpt.isPresent(), is(false));
        //находим в БД автоследования стратегию и проверяем, что увеличилось на 1 значение количества подписчиков на стратегию
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Количество подписчиков на стратегию не равно", strategyMaster.getSlavesCount(), is(0));

    }

    //***методы для работы тестов**************************************************************************
    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWithContractAndStrategy(String SIEBEL_ID, UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                             ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
        //находим данные по клиенту в БД social
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        //создаем запись о клиенте в tracking.client
        clientMaster = clientService.createClient(investId, ClientStatusType.registered, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString()));
        // создаем запись о договоре клиента в tracking.contract
        contractMaster = new Contract()
            .setId(contractId)
            .setClientId(clientMaster.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contractMaster = contractService.saveContract(contractMaster);
        //создаем запись о стратегии клиента
        strategyMaster = new Strategy()
            .setId(strategyId)
            .setContract(contractMaster)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(1);
        strategyMaster = trackingService.saveStrategy(strategyMaster);
    }
}
