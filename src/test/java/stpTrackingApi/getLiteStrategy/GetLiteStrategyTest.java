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
import ru.qa.tinkoff.swagger.tracking.model.GetLiteStrategyResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.*;

import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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
public class GetLiteStrategyTest {
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

    Strategy strategyMaster;
    String contractIdMaster;
    UUID strategyId;

    String siebelIdMaster;
    String title;
    String description;
    UUID investIdMaster;


    @BeforeAll void getDataFromAccount() {
        siebelIdMaster = stpSiebel.siebelIdApiMaster;
        title = steps.getTitleStrategy();
        description = "new test стратегия autotest";
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
    @AllureId("1346576")
    @DisplayName("C1346576.getLiteStrategy - Получение облегченных данных по стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Получение облегченных данных по стратегии")
    void C1346576() {
        strategyId = UUID.randomUUID();
        GetLiteStrategyResponse getLiteStrategyResponse;
        //создаем в БД tracking данные: client, contract, strategy в статусе draft
        steps.createClientWintContractAndStrategyFee(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, "0.3", "0.05", false, null,"TEST","TEST11");

        getLiteStrategyResponse = getSignalsResponse(strategyId);
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategyMaster = strategyService.getStrategy(strategyId);
        checkGetLiteStrategyResponse(getLiteStrategyResponse, strategyMaster);

        //Удаляем стратегию и повторно вызываем метод(Получим данные из кэша)
        strategyService.deleteStrategy(steps.strategyMaster);
        getLiteStrategyResponse = getSignalsResponse(strategyId);
        checkGetLiteStrategyResponse(getLiteStrategyResponse, strategyMaster);
    }


    GetLiteStrategyResponse getSignalsResponse (UUID strategyId) {
        GetLiteStrategyResponse getLiteStrategyResponse = strategyApiCreator.get().getLiteStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategyResponse.class));
        return getLiteStrategyResponse;
    }

    void checkGetLiteStrategyResponse (GetLiteStrategyResponse getLiteStrategyResponse, Strategy strategyMaster){
        assertThat("id не равно " + strategyId, getLiteStrategyResponse.getId(), is(strategyMaster.getId()));
        assertThat("title не равно " + title, getLiteStrategyResponse.getTitle(), is(strategyMaster.getTitle()));
        assertThat("baseCurrency не равно " + strategyMaster.getBaseCurrency().toString(), getLiteStrategyResponse.getBaseCurrency().toString(), is(strategyMaster.getBaseCurrency().toString()));
        assertThat("status не равно " + strategyMaster.getStatus().toString(), getLiteStrategyResponse.getStatus().toString(), is(strategyMaster.getStatus().toString()));
    }

}
