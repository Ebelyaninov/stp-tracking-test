package stpTrackingApi.getMasterStrategies;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
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
import org.springframework.context.annotation.Description;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.model.GetMasterStrategiesResponse;
import ru.qa.tinkoff.swagger.tracking.model.MasterStrategy;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("getMasterStrategies Получение списка всех стратегий ведущего")
@Feature("TAP-10153")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)

@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class

})
public class GetMasterStrategiesTest {
    @Autowired
    ByteToByteSenderService byteToByteSenderService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StpTrackingApiSteps steps;



    StrategyApi strategyApi = ru.qa.tinkoff.swagger.tracking.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.tracking.invoker.ApiClient.Config.apiConfig()).strategy();


    String contractIdMaster1;
    String contractIdMaster2;
    String SIEBEL_ID_MASTER = "1-BABKO0G";
    UUID strategyId1;
    UUID strategyId2;
    Strategy strategy1;
    Strategy strategy2;
    Contract contract1;
    Contract contract2;
    Client clientMaster;

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                trackingService.deleteStrategy(strategy1);
            } catch (Exception e) {
            }
            try {
                trackingService.deleteStrategy(strategy2);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contract1);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contract2);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientMaster);
            } catch (Exception e) {
            }
        });
    }

    @SneakyThrows
    @Test
    @AllureId("1184813")
    @DisplayName("C1184813.GetMasterStrategies.Получение списка всех стратегий ведущего")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения списка всех стратегий ведущего")
    void C1184813() {
        String SIEBEL_ID_MASTER = "5-JSZXGLRT";
        Random ran = new Random();
        int x = ran.nextInt(6) + 5;
        String title1 = "autotest1 " + String.valueOf(x);
        String description1 = "new test стратегия autotest1";
        int y = ran.nextInt(6) + 5;
        String title2 = "autotest2 " + String.valueOf(y);
        String description2 = "new test стратегия autotest1";
        strategyId1 = UUID.randomUUID();
        strategyId2 = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster1 = resAccountMaster.getBrokerAccounts().get(0).getId();
        contractIdMaster2 = resAccountMaster.getBrokerAccounts().get(1).getId();
        clientMaster = steps.createClientWithProfile(SIEBEL_ID_MASTER, investIdMaster);
        //создаем в БД tracking данные по ведущему: client, contract, strategy в статусе active
        steps.createContractAndStrategy(clientMaster, contractIdMaster1, null, ContractState.untracked,
            strategyId1, title1, description1, StrategyCurrency.rub,
            ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 4);
        steps.createContractAndStrategy(clientMaster, contractIdMaster2, null, ContractState.untracked,
            strategyId2, title2, description2, StrategyCurrency.usd,
            ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, null);
        contract1 = contractService.getContract(contractIdMaster1);
        contract2 = contractService.getContract(contractIdMaster2);
        // вызываем метод getMasterStrategies
        GetMasterStrategiesResponse getMasterStrategiesResponse = strategyApi.getMasterStrategies()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterStrategiesResponse.class));
        //сохраняем данные по стратегиям в списки
        List<MasterStrategy> masterStrategy1 = getMasterStrategiesResponse.getItems().stream()
            .filter(ms -> ms.getTitle().equals(title1))
            .collect(Collectors.toList());
        List<MasterStrategy> masterStrategy2 = getMasterStrategiesResponse.getItems().stream()
            .filter(ms -> ms.getTitle().equals(title2))
            .collect(Collectors.toList());
        //находим стратегии в базе
        strategy1 = strategyService.getStrategy(strategyId1);
        strategy2 = strategyService.getStrategy(strategyId2);
        //проверяем данные по стратегиям
        checkParamStrategy(masterStrategy1, strategy1);
        checkParamStrategy(masterStrategy2, strategy2);
    }


    @SneakyThrows
    @Test
    @AllureId("1185892")
    @DisplayName("C1185892.GetMasterStrategies.Получение списка всех стратегий ведущего.У мастера нет стратегий")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения списка всех стратегий ведущего")
    void C1185892() {
        // вызываем метод getMasterStrategies
        GetMasterStrategiesResponse getMasterStrategiesResponse = strategyApi.getMasterStrategies()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterStrategiesResponse.class));
        assertThat("strategy.id не равно", getMasterStrategiesResponse.getItems().size(), is(0));
    }


    private static Stream<Arguments> provideRequiredParam() {
        return Stream.of(
            Arguments.of(null, "4.5.6", "android"),
            Arguments.of("trading-invest", null, "android"),
            Arguments.of("trading-invest", "4.5.6", null)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideRequiredParam")
    @AllureId("1184814")
    @DisplayName("C1184814.CreateSubscription.Валидация обязательных парамертов: x-app-name, x-app-version, x-platform, x-tcs-siebel-id, contractId, strategyId")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения списка всех стратегий ведущего")
    void C1184814(String name, String version, String platform) throws Exception {
        StrategyApi.GetMasterStrategiesOper getMasterStrategiesResponse = strategyApi.getMasterStrategies()
            .xTcsSiebelIdHeader(SIEBEL_ID_MASTER)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            getMasterStrategiesResponse = getMasterStrategiesResponse.xAppNameHeader(name);
        }
        if (version != null) {
            getMasterStrategiesResponse = getMasterStrategiesResponse.xAppVersionHeader(version);
        }
        if (platform != null) {
            getMasterStrategiesResponse = getMasterStrategiesResponse.xPlatformHeader(platform);
        }
        getMasterStrategiesResponse.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterStrategiesResponse.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("1185914")
    @DisplayName("C1185914.GetMasterStrategies.Валидация входного запроса: обязательные параметры: заголовок x-tcs-siebel-id")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения списка всех стратегий ведущего")
    void C1185914() {
        // вызываем метод getMasterStrategies
        StrategyApi.GetMasterStrategiesOper getMasterStrategiesResponse = strategyApi.getMasterStrategies()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .respSpec(spec -> spec.expectStatusCode(401));
        getMasterStrategiesResponse.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterStrategiesResponse.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("InsufficientPrivileges"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно прав"));
    }

    @SneakyThrows
    @Test
    @AllureId("1185919")
    @DisplayName("C1185919.GetMasterStrategies.Не найден клиент clientId в clientIdCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения списка всех стратегий ведущего")
    void C1185919() {
        // вызываем метод getMasterStrategies
        StrategyApi.GetMasterStrategiesOper getMasterStrategiesResponse = strategyApi.getMasterStrategies()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader("7-227G1PKDH")
            .respSpec(spec -> spec.expectStatusCode(422));
        getMasterStrategiesResponse.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterStrategiesResponse.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    void checkParamStrategy(List<MasterStrategy> masterStrategy, Strategy strategy) {
        assertThat("strategy.id не равно", masterStrategy.get(0).getId(), is(strategy.getId()));
        assertThat("strategy.title не равно", masterStrategy.get(0).getTitle(), is(strategy.getTitle()));
        assertThat("strategy.status не равно", masterStrategy.get(0).getStatus().getValue(), is(strategy.getStatus().toString()));

    }
}