package stpTrackingAdminApi.deleteDraftStrategy;


import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Owner;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.creator.InvestAccountCreator;
import ru.qa.tinkoff.creator.adminCreator.AdminApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.StrategyApiAdminCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioPositionRetention;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioPositionRetentionDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.ErrorResponse;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.StrategyApi;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("deleteDraftStrategy - Удаление draft-стратегии администратором")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Subfeature("Альтернативные сценарии")
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("deleteDraftStrategy")})
@Owner("ext.ebelyaninov")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    KafkaAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    AdminApiCreatorConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    ApiCreatorConfiguration.class
})
public class DeleteDraftStrategyErrorTest {
    @Autowired
    TrackingService trackingService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ContractService contractService;
    @Autowired
    ClientService clientService;
    @Autowired
    StpTrackingAdminSteps stpTrackingAdminSteps;
    @Autowired
    StpSiebel siebel;
    @Autowired
    StrategyApiAdminCreator strategyApiStrategyApiAdminCreator;
    @Autowired
    InvestAccountCreator<BrokerAccountApi> brokerAccountApiCreator;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    MasterPortfolioPositionRetentionDao masterPortfolioPositionRetentionDao;

    MasterPortfolioPositionRetention masterPortfolioPositionRetention;

    String xApiKey = "x-api-key";
    BigDecimal expectedRelativeYield = new BigDecimal(10.00);
    String title;
    String description;
    UUID investIdMaster;
    String contractIdMaster;
    UUID strategyId;

    @BeforeAll
    void createTestData() {
        title = stpTrackingAdminSteps.getTitleStrategy();
        description = "Стратегия Autotest 001 - Описание";
        //Получаем данные по клиенту в API-Сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery(false)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        stpTrackingAdminSteps.deleteDataFromDb(contractIdMaster, investIdMaster);
        strategyId = UUID.randomUUID();
    }

    @BeforeEach
    void createTestDataInDb (){
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        stpTrackingAdminSteps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investIdMaster, null, contractIdMaster, ContractState.untracked,
            strategyId, stpTrackingAdminSteps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, LocalDateTime.now(), 3, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04");
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = new ArrayList<>();
        stpTrackingAdminSteps.createMasterPortfolio(contractIdMaster, strategyId,1,"3000", masterPos);
        createDateMasterPortfolioPositionRetention(strategyId, 10, 2, "days");
    }


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                trackingService.deleteStrategy(stpTrackingAdminSteps.strategy);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(stpTrackingAdminSteps.contract);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(stpTrackingAdminSteps.client);
            } catch (Exception e) {
            }
            try {
                masterPortfolioDao.deleteMasterPortfolio(contractIdMaster, strategyId);
            } catch (Exception e) {
            }
            try {
                masterPortfolioPositionRetentionDao.deleteMasterPortfolioPositionRetention(strategyId);
            } catch (Exception e) {
            }
        });
    }


    private static Stream<Arguments> provideStringsForHeadersGetExchangePosition() {
        return Stream.of(
            Arguments.of(null, "tracking_admin"),
            Arguments.of("invest", null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideStringsForHeadersGetExchangePosition")
    @AllureId("1737374")
    @DisplayName("C1737374.deleteDraftStrategy. Получение 400 ошибки")
    @Description("Удаление draft-стратегии администратором")
    void C1737374(String xAppName, String xTcsLogin) {
        //Вызываем метод deleteDraftStrategy
        ErrorResponse getErrorResponse = deleteDraftStrategy(strategyId.toString(), xAppName, xTcsLogin,400);
        //Проверяем, что  удалили стратегию \ мастер портфель и запись с master_portfolio_position_retention
        confirmDeletedParams(false, false, false);
        validateErrorResponse(getErrorResponse, "0344-00-Z99", "Сервис временно недоступен");
    }

    @Test
    @AllureId("1738941")
    @DisplayName("C1738941.deleteDraftStrategy. Получение 401 ошибки")
    @Description("Удаление draft-стратегии администратором")
    void C1738941() {
        strategyApiStrategyApiAdminCreator.get().deleteDraftStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking228"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(ResponseBodyData::asString);
    }



    //*** Методы для работы тестов ***
//*****************************************************************************************************
    void createDateMasterPortfolioPositionRetention(UUID strategyId, int days, int hours, String value) {
        masterPortfolioPositionRetention = MasterPortfolioPositionRetention.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .value(value)
            .build();
        masterPortfolioPositionRetentionDao.insertIntoMasterPortfolioPositionRetention(masterPortfolioPositionRetention);
    }

    ErrorResponse deleteDraftStrategy (String strategyId, String xAppNameHeader, String xTcsLoginHeader, int expectedStatusCode) {

        StrategyApi.DeleteDraftStrategyOper deleteDraftStrategy = strategyApiStrategyApiAdminCreator.get().deleteDraftStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"));

        if (xAppNameHeader != null) {
            deleteDraftStrategy = deleteDraftStrategy.xAppNameHeader(xAppNameHeader);
        }
        if (xTcsLoginHeader != null) {
            deleteDraftStrategy = deleteDraftStrategy.xTcsLoginHeader(xTcsLoginHeader);
        }
        if (strategyId != null) {
            deleteDraftStrategy = deleteDraftStrategy.strategyIdPath(strategyId);
        }

        deleteDraftStrategy.respSpec(spec -> spec.expectStatusCode(expectedStatusCode));

        ErrorResponse getErrorResponse = deleteDraftStrategy.execute(response -> response.as(ErrorResponse.class));
        return getErrorResponse;
    }

    void confirmDeletedParams (Boolean strategyWasDeleted, Boolean isMasterPortfolioWasDeleted, Boolean masterPortfolioPositionRetentionWasDeleted){
        assertThat("Не удалили записи strategy", strategyService.findListStrategyByContractId(contractIdMaster).isEmpty(), is(strategyWasDeleted));
        assertThat("Не удалили записи master_portfolio", masterPortfolioDao.findLatestMasterPortfolio(contractIdMaster, strategyId).isEmpty(), is(isMasterPortfolioWasDeleted));
        assertThat("Не удалили записи masterPortfolioPositionRetention", masterPortfolioPositionRetentionDao.getListMasterPortfolioPositionRetention(strategyId).isEmpty(), is(masterPortfolioPositionRetentionWasDeleted));
    }

    void validateErrorResponse (ErrorResponse getErrorResponse, String errorCode, String errorMessage){
        assertThat("errorCode != " + errorCode, getErrorResponse.getErrorCode(), equalTo(errorCode));
        assertThat("errorMessage != " + errorMessage, getErrorResponse.getErrorMessage(), equalTo(errorMessage));
    }

}
