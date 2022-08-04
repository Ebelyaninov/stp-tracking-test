package stpTrackingApi.getMasterStrategies;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Description;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreator;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.model.ErrorResponse;
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
import static org.junit.jupiter.api.Assertions.assertAll;

@Slf4j
@Epic("getMasterStrategies Получение списка всех стратегий ведущего")
@Feature("TAP-10153")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@Tags({@Tag("stp-tracking-api"), @Tag("getMasterStrategies")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)

@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class,
})
public class GetMasterStrategiesTest {
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
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ApiCreator<StrategyApi> strategyApiCreator;

    String contractIdMaster1;
    String contractIdMaster2;
    String contractIdMaster3;
    String SIEBEL_ID_MASTER;
    UUID strategyId1;
    UUID strategyId2;
    UUID strategyId3;
    Strategy strategy1;
    Strategy strategy2;
    Strategy strategy3;
    Contract contract1;
    Contract contract2;
    Contract contract3;
    Client clientMaster;


    @BeforeAll
    void getDataFromAccount() {
        SIEBEL_ID_MASTER = stpSiebel.siebelIdApiMaster;
    }

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
                trackingService.deleteStrategy(strategy3);
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
                contractService.deleteContract(contract3);
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
        String SIEBEL_ID_MASTER = "5-KZQQI0K8";
        Random ran = new Random();
        int x = ran.nextInt(6) + 5;
        String title1 = "autotest1 " + String.valueOf(x);
        String description1 = "new test стратегия autotest1";
        int y = ran.nextInt(6) + 5;
        String title2 = "autotest2 " + String.valueOf(y);
        String description2 = "new test стратегия autotest1";
        int z = ran.nextInt(6) + 5;
        String title3 = "autotest3 " + String.valueOf(z);
        String description3 = "new test стратегия autotest1";
        strategyId1 = UUID.randomUUID();
        strategyId2 = UUID.randomUUID();
        strategyId3 = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster1 = resAccountMaster.getBrokerAccounts().get(0).getId();
        contractIdMaster2 = resAccountMaster.getBrokerAccounts().get(1).getId();
        contractIdMaster3 = resAccountMaster.getBrokerAccounts().get(2).getId();
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
        steps.createContractAndStrategy(clientMaster, contractIdMaster3, null, ContractState.untracked,
            strategyId3, title3, description3, StrategyCurrency.usd,
            ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.frozen, 0, LocalDateTime.now(), 3);
        contract1 = contractService.getContract(contractIdMaster1);
        contract2 = contractService.getContract(contractIdMaster2);
        contract3 = contractService.getContract(contractIdMaster3);
        // вызываем метод getMasterStrategies
        GetMasterStrategiesResponse getMasterStrategiesResponse = strategyApiCreator.get().getMasterStrategies()
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
        List<MasterStrategy> masterStrategy3 = getMasterStrategiesResponse.getItems().stream()
            .filter(ms -> ms.getTitle().equals(title3))
            .collect(Collectors.toList());
        //находим стратегии в базе
        strategy1 = strategyService.getStrategy(strategyId1);
        strategy2 = strategyService.getStrategy(strategyId2);
        strategy3 = strategyService.getStrategy(strategyId3);
        //проверяем данные по стратегиям
        checkParamStrategy(masterStrategy1, strategy1);
        checkParamStrategy(masterStrategy2, strategy2);
        checkParamStrategy(masterStrategy3, strategy3);
    }


    @SneakyThrows
    @Test
    @AllureId("1185892")
    @DisplayName("C1185892.GetMasterStrategies.Получение списка всех стратегий ведущего.У мастера нет стратегий")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения списка всех стратегий ведущего")
    void C1185892() {
        // вызываем метод getMasterStrategies
        GetMasterStrategiesResponse getMasterStrategiesResponse = strategyApiCreator.get().getMasterStrategies()
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
        StrategyApi.GetMasterStrategiesOper getMasterStrategiesResponse = strategyApiCreator.get().getMasterStrategies()
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
        ErrorResponse errorResponse = getMasterStrategiesResponse.execute(ErrorResponse -> ErrorResponse.as(ErrorResponse.class));
        checkErrorMessage(errorResponse, "Error", "Сервис временно недоступен");
    }

    @SneakyThrows
    @Test
    @AllureId("1185914")
    @DisplayName("C1185914.GetMasterStrategies.Валидация входного запроса: обязательные параметры: заголовок x-tcs-siebel-id")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения списка всех стратегий ведущего")
    void C1185914() {
        // вызываем метод getMasterStrategies
        ErrorResponse getMasterStrategiesResponse = strategyApiCreator.get().getMasterStrategies()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(ErrorResponse -> ErrorResponse.as(ErrorResponse.class));;
        checkErrorMessage(getMasterStrategiesResponse, "InsufficientPrivileges", "Недостаточно прав");
    }

    @SneakyThrows
    @Test
    @AllureId("1185919")
    @DisplayName("C1185919.GetMasterStrategies.Не найден клиент clientId в clientIdCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения списка всех стратегий ведущего")
    void C1185919() {
        // вызываем метод getMasterStrategies
        ErrorResponse getMasterStrategiesResponse = strategyApiCreator.get().getMasterStrategies()
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader("7-227G1PKDH")
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(ErrorResponse -> ErrorResponse.as(ErrorResponse.class));
        checkErrorMessage(getMasterStrategiesResponse, "Error", "Сервис временно недоступен");
    }

    void checkParamStrategy(List<MasterStrategy> masterStrategy, Strategy strategy) {
        assertAll("Выполняем проверки",
            () -> assertThat("strategy.id не равно", masterStrategy.get(0).getId(), is(strategy.getId())),
            () -> assertThat("strategy.title не равно", masterStrategy.get(0).getTitle(), is(strategy.getTitle())),
            () -> assertThat("strategy.status не равно", masterStrategy.get(0).getStatus().getValue(), is(strategy.getStatus().toString()))
        );
    }

    void checkErrorMessage (ErrorResponse errorResponse, String errorCode, String errorMessage){
        assertAll("Выполняем проверки",
            () -> assertThat("код ошибки не равно", errorResponse.getErrorCode(), is(errorCode)),
            () -> assertThat("Сообщение об ошибке не равно", errorResponse.getErrorMessage(), is(errorMessage))
        );
    }
}