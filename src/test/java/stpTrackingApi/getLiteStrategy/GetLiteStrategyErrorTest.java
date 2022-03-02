package stpTrackingApi.getLiteStrategy;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Owner;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreator;
import ru.qa.tinkoff.creator.StrategyApiCreator;
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
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("getLiteStrategy Получение облегченных данных по стратегии")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@Tags({@Tag("stp-tracking-api"), @Tag("getLiteStrategy")})
@Owner("ext.ebelyaninov")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StrategyApiCreator.class,
    StpTrackingSiebelConfiguration.class
})
public class GetLiteStrategyErrorTest {
    @Autowired
    StrategyService strategyService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    ApiCreator<StrategyApi> strategyApiCreator;
    @Autowired
    StpSiebel stpSiebel;

    String contractIdMaster;
    UUID strategyId;

    String siebelIdMaster;
    String title;
    String description;
    UUID investIdMaster;


    @BeforeAll
    void getDataFromAccount() {
        siebelIdMaster = stpSiebel.siebelIdApiMaster;
        title = steps.getTitleStrategy();
        description = "new test стратегия клиента " + siebelIdMaster;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.createEventInTrackingContractEvent(contractIdMaster);
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {

            try {
                strategyService.deleteStrategy(steps.strategyMaster);
            } catch (Exception e)
            {
            }
            try {
                contractService.deleteContract(steps.contractMaster);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClientById(investIdMaster);
            } catch (Exception e) {
            }
        });
    }

    @Test
    @AllureId("1346578")
    @DisplayName("C1346578.getLiteStrategy - Не удалось получить данные по стратегии")
    @Subfeature("Альтернативные сценарии")
    @Description("Получение облегченных данных по стратегии")
    void C1346578() {
        strategyId = UUID.randomUUID();
        ErrorResponse getLiteStrategyErrorResponse = getLiteStrategyErrorResponse(strategyId, 422);
        //Проверяем ответ
        checkGetLiteStrategyErrorResponse(getLiteStrategyErrorResponse, "StrategyNotFound","Стратегия не найдена");
    }

    @Test
    @AllureId("1346580")
    @DisplayName("C1346580.getLiteStrategy - Не передали заголовок x-tcs-siebel-id")
    @Subfeature("Альтернативные сценарии")
    @Description("Получение облегченных данных по стратегии")
    void C1346580() {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategyFee(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), "0.3", "0.05", false, null,"TEST","TEST11");

        ErrorResponse getLiteStrategyErrorResponse = strategyApiCreator.get().getLiteStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.as(ErrorResponse.class));
        //Проверяем ответ
        checkGetLiteStrategyErrorResponse(getLiteStrategyErrorResponse, "InsufficientPrivileges","Недостаточно прав");
    }

    private static Stream<Arguments> provideStringsForHeadersGetLiteStrategy() {
        return Stream.of(
            Arguments.of(null, "6.0.5", "ios", false),
            Arguments.of("invest", null, "ios", false),
            Arguments.of("invest", "6.0.5", null, false),
            Arguments.of("invest", "6.0.5", "ios", true)
        );
    }

    @ParameterizedTest
    @MethodSource("provideStringsForHeadersGetLiteStrategy")
    @AllureId("1346580")
    @DisplayName("CC346580.getLiteStrategy - Валидация входного запроса")
    @Subfeature("Альтернативные сценарии")
    @Description("Получение облегченных данных по стратегии")
    void C1346577(String xAppName, String xAppVersionHeader, String xPlatformHeader, Boolean stringStrategy) {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategyFee(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), "0.3", "0.05", false, null,"TEST","TEST11");

        StrategyApi.GetLiteStrategyOper getLiteStrategyError = strategyApiCreator.get().getLiteStrategy()
            .strategyIdPath(strategyId)
            .xTcsSiebelIdHeader(siebelIdMaster)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (stringStrategy.equals(true)){
            getLiteStrategyError = getLiteStrategyError.strategyIdPath("test");
        }
        if (xAppName != null){
            getLiteStrategyError = getLiteStrategyError.xAppNameHeader(xAppName);
        }
        if (xAppVersionHeader != null){
            getLiteStrategyError = getLiteStrategyError.xAppVersionHeader(xAppVersionHeader);
        }
        if (xPlatformHeader != null){
            getLiteStrategyError = getLiteStrategyError.xPlatformHeader(xPlatformHeader);
        }
        ErrorResponse getLiteStrategyErrorResponse = getLiteStrategyError
            .execute(response -> response.as(ErrorResponse.class));
        //Проверяем ответ
        checkGetLiteStrategyErrorResponse(getLiteStrategyErrorResponse, "Error","Сервис временно недоступен");
    }

    ErrorResponse getLiteStrategyErrorResponse (UUID strategyId, int statusCode) {
        ErrorResponse getLiteStrategyErrorResponse = strategyApiCreator.get().getLiteStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(statusCode))
            .execute(response -> response.as(ErrorResponse.class));
        return getLiteStrategyErrorResponse;
    }

    void checkGetLiteStrategyErrorResponse (ErrorResponse getLiteStrategyResponse, String errorCode, String errorMessage){
        assertThat("errorCode не равно " + errorCode, getLiteStrategyResponse.getErrorCode(), is(errorCode));
        assertThat("errorMessage не равно " + errorMessage, getLiteStrategyResponse.getErrorMessage(), is(errorMessage));
    }
}
