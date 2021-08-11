package stpTrackingAdminApi.getStrategies;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.transaction.PlatformTransactionManager;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.SptTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.model.GetStrategiesResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@Slf4j
@Epic("getStrategies - Получение списка стратегий")
@Feature("TAP-10352")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class
})
public class GetStrategiesTest {
    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
//    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient
//        .api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();

    @Autowired
    BillingService billingService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StpTrackingAdminSteps steps;
    @Autowired
    StrategyService strategyService;
    @Autowired
    PlatformTransactionManager billingTransactionManager;
    Client client;
    Contract contract;
    Strategy strategy;
    String SIEBEL_ID = "5-JDFC5N71";
    String xApiKey = "x-api-key";

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                strategyService.deleteStrategy(steps.strategy);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(steps.contract);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(steps.client);
            } catch (Exception e) {
            }
        });
    }

    private static Stream<Arguments> provideLimit() {
        return Stream.of(
            Arguments.of(1),
            Arguments.of(2),
            Arguments.of(5)
        );
    }


    @ParameterizedTest
    @MethodSource("provideLimit")
    @AllureId("1041091")
    @DisplayName("C1041091.getStrategies.Получение списка стратегий, передан параметр limit")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка всех торговый стратегий в автоследовании.")
    void C1041091(Integer limit) {
        //вызываем метод getStrategys
        GetStrategiesResponse responseExep = strategyApi.getStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .limitQuery(limit)
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategiesResponse.class));
        //проверяем, данные в сообщении
        assertThat("Количество возвращаемых записей не равно", responseExep.getItems().size(), is(limit));
    }


    @Test
    @AllureId("1041616")
    @DisplayName("C1041616.getStrategies.Получение списка стратегий, успешный ответ проверка маппинга")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка всех торговый стратегий в автоследовании.")
    void C1041616() {
        String title = "Стратегия Autotest - Заголовок";
        String description = "Общий, недетализированный план, охватывающий длительный период времени, способ достижения сложной цели, позднее вообще какой-либо деятельности человека.";
        Integer score = 2;
        UUID strategyId = UUID.randomUUID();
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        //Получаем данные по клиенту в API-Сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //Создаем клиента в tracking: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score);
        strategy = strategyService.getStrategy(strategyId);
        Integer position = strategy.getPosition();
        List<Strategy> strategys = strategyService.getStrategysByPositionAndLimitmit(position, 1);
        String contractIdNew = strategys.get(0).getContract().getId();
        contract = contractService.getContract(contractIdNew);
        client = clientService.getClient(contract.getClientId());
        String nickName = client.getSocialProfile() == null ? null : client.getSocialProfile().getNickname();
        //вызываем метод getStrategys
        GetStrategiesResponse responseExep = strategyApi.getStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .limitQuery(1)
            .cursorQuery(position)
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategiesResponse.class));
        String nickNameOwner = responseExep.getItems().get(0).getOwner().getSocialProfile() == null ?
            null : responseExep.getItems().get(0).getOwner().getSocialProfile().getNickname();
        //проверяем, данные в сообщении
        assertThat("номера стратегии не равно", strategys.get(0).getId(), is(responseExep.getItems().get(0).getId()));
        assertThat("статус стратегии не равно", strategys.get(0).getStatus().toString(), is(responseExep.getItems().get(0).getStatus().toString()));
        assertThat("название стратегии не равно", (strategys.get(0).getTitle()), is(responseExep.getItems().get(0).getTitle()));
        assertThat("валюта стратегии не равно", (strategys.get(0).getBaseCurrency()).toString(), is(responseExep.getItems().get(0).getBaseCurrency().toString()));
        assertThat("риск-профиль стратегии не равно", (strategys.get(0).getRiskProfile().toString()), is(responseExep.getItems().get(0).getRiskProfile().toString()));
        assertThat("описание стратегии не равно", strategys.get(0).getDescription(), is(responseExep.getItems().get(0).getDescription()));
        assertThat("оценка стратегии не равна", (strategys.get(0).getScore()), is(responseExep.getItems().get(0).getScore()));
        assertThat("автор стратегии не равно", nickName, is(nickNameOwner));
    }


    @Test
    @AllureId("1041090")
    @DisplayName("C1041090.getStrategies.Получение списка стратегий, nextCursor is null")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка всех торговый стратегий в автоследовании.")
    void C1041090() {
        String title = "Стратегия Autotest - Заголовок";
        String description = "Общий, недетализированный план, охватывающий длительный период времени, способ достижения сложной цели, позднее вообще какой-либо деятельности человека.";
        Integer score = 2;
        UUID strategyId = UUID.randomUUID();
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        //Получаем данные по клиенту в API-Сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //Создаем клиента в tracking: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score);
        List<Strategy> strategys = strategyService.getStrategysByOrderPosition();
        int size = strategys.size();
        Integer position = strategys.get(size - 1).getPosition();
        //вызываем метод getStrategys
        GetStrategiesResponse responseExep = strategyApi.getStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .cursorQuery(position)
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategiesResponse.class));
        assertThat("Крайний strategy.position не равно", responseExep.getNextCursor(), is(nullValue()));
        assertThat("hasNext не равно", responseExep.getHasNext(), is(false));
        assertThat("items возвращаемых записей не равно", responseExep.getItems().size(), is(0));
    }

    @Test
    @AllureId("1043600")
    @DisplayName("C1043600.getStrategies.Получение списка стратегий, проверка hasNext")
    @Subfeature("Успешные сценарии")
    @Description("Метод необходим для получения списка всех торговый стратегий в автоследовании.")
    void C1043600() {
        String title = "Стратегия Autotest - Заголовок";
        String description = "Общий, недетализированный план, охватывающий длительный период времени, способ достижения сложной цели, позднее вообще какой-либо деятельности человека.";
        Integer score = 2;
        UUID strategyId = UUID.randomUUID();
        SocialProfile socialProfile = steps.getProfile(SIEBEL_ID);
        //Получаем данные по клиенту в API-Сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //Создаем клиента в tracking: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(investId, socialProfile, contractId, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), score);
        List<Strategy> strategys = strategyService.getStrategysByOrderPosition();
        int size = strategys.size();
        //вызываем метод getStrategys
        GetStrategiesResponse responseExep = strategyApi.getStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .cursorQuery(strategys.get(size - 2).getPosition())
            .limitQuery(1)
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategiesResponse.class));
        assertThat("Крайний strategy.position не равно", responseExep.getNextCursor(), is(strategys.get(size - 1).getPosition().toString()));
        assertThat("hasNext не равно", responseExep.getHasNext(), is(false));
        //вызываем метод getStrategys
        GetStrategiesResponse responseEx = strategyApi.getStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .cursorQuery(strategys.get(size - 3).getPosition())
            .limitQuery(1)
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategiesResponse.class));
        assertThat("Крайний strategy.position не равно", responseEx.getNextCursor(), is(strategys.get(size - 2).getPosition().toString()));
        assertThat("hasNext не равно", responseEx.getHasNext(), is(true));
    }


//    @Test
//    @AllureId("1043702")
//    @DisplayName("C1043702.getStrategies.Получение списка стратегий, передан параметр limit")
//    @Subfeature("Успешные сценарии")
//    @Description("Метод необходим для получения списка всех торговый стратегий в автоследовании.")
//    void C1043702() {
//        List<UUID> strategyIds = new ArrayList<>();
//        List<String> contractIds = new ArrayList<>();
//        List<UUID> clientIds = new ArrayList<>();
//        try {
//            for (int i = 0; i < 120; i++) {
//                UUID strategyId = UUID.randomUUID();
//                String title = "Стратегия Autotest - Заголовок";
//                String description = "Стратегия Autotest - Описание";
//                BrokerAccount findValidAccount = billingService.getFirstValid();
//                UUID investId = findValidAccount.getInvestAccount().getId();
//                String contractId = findValidAccount.getId();
//                try {
//                    steps.createClientWithContractAndStrategy(investId, null, contractId, null, ContractState.untracked,
//                        strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
//                        StrategyStatus.draft, 0, null, null);
//                } catch (Exception e) {
//                    log.error("завис на создании");
//                }
//
//                strategyIds.add(strategyId);
//                contractIds.add(contractId);
//                clientIds.add(investId);
//            }
//            GetStrategiesResponse responseExep = strategyApi.getStrategies()
//                .reqSpec(r -> r.addHeader("api-key", "tracking"))
//                .xAppNameHeader("invest")
//                .limitQuery(101)
//                .xTcsLoginHeader("tracking_admin")
//                .respSpec(spec -> spec.expectStatusCode(200))
//                .execute(response -> response.as(GetStrategiesResponse.class));
//            //проверяем, данные в сообщении
////                assertThat("Количество возвращаемых записей не равно", responseExep.getItems().size(), is(100));
//
//        } finally {
////
//            strategyService.deleteStrategyByIds(strategyIds);
//            contractService.deleteStrategyByIds(contractIds);
//            clientService.deleteStrategyByIds(clientIds);
//        }
////    }


    private static Stream<Arguments> provideStringsForHeadersgetStrategies() {
        return Stream.of(
            Arguments.of(null, "tracking_admin"),
            Arguments.of("trading-invest", null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideStringsForHeadersgetStrategies")
    @AllureId("1041093")
    @DisplayName("C1041093.GetStrategys.Валидация запроса: Headers: x-app-name, x-tcs-login")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения информации о торговой стратегии по ее идентификатору.")
    void C1041093(String name, String login) {
        //вызываем метод confirmMasterClient
        StrategyApi.GetStrategiesOper getStrategies = strategyApi.getStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            getStrategies = getStrategies.xAppNameHeader(name);
        }
        if (login != null) {
            getStrategies = getStrategies.xTcsLoginHeader(login);
        }
        getStrategies.execute(ResponseBodyData::asString);
    }


    @Test
    @AllureId("1041133")
    @DisplayName("C1041133.GetStrategys.Авторизация: не передаем X-API-KEY")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C1041133() {
        //получаем данные по клиенту  в api сервиса счетов
        strategyApi.getStrategies()
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
    }

    @Test
    @AllureId("1041134")
    @DisplayName("C1041134.GetStrategys.Авторизация: Неверное значение X-API-KEY")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C1041134() {
        strategyApi.getStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, "trading"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
    }

}
