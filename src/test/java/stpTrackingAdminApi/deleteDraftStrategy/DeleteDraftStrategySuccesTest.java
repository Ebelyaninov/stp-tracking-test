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
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


@Slf4j
@Epic("deleteDraftStrategy - Удаление draft-стратегии администратором")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Subfeature("Успешные сценарии")
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
public class DeleteDraftStrategySuccesTest {
    @Autowired
    ProfileService profileService;
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
    Profile profile;
    SocialProfile socialProfile;
    UUID investIdMaster;
    String contractIdMaster;
    UUID strategyId;


    @BeforeAll
    void createTestData() {
        title = stpTrackingAdminSteps.getTitleStrategy();
        description = "Стратегия Autotest 001 - Описание";
        //Находим клиента в БД social
        profile = profileService.getProfileBySiebelId(siebel.siebelIdAdmin);
        socialProfile = stpTrackingAdminSteps.getProfile(siebel.siebelIdAdmin);
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
        stpTrackingAdminSteps.deleteDataFromDb(siebel.siebelIdAdmin);
    }


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                trackingService.deleteStrategy(strategyService.getStrategy(strategyId));
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

    @Test
    @AllureId("1737376")
    @DisplayName("C1737376.deleteDraftStrategy. Удаление draft-стратегии администратором")
    @Subfeature("Успешные сценарии")
    @Description("Удаление draft-стратегии администратором")
    void C1737376() {
        strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        stpTrackingAdminSteps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investIdMaster, null, contractIdMaster, ContractState.untracked,
            strategyId, stpTrackingAdminSteps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, LocalDateTime.now(), 3, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = new ArrayList<>();
        stpTrackingAdminSteps.createMasterPortfolio(contractIdMaster, strategyId,1,"3000", masterPos);
        createDateMasterPortfolioPositionRetention(strategyId, 10, 2, "days");
        //Вызываем метод deleteDraftStrategy
        deleteDraftStrategy(strategyId);
        //Проверяем, что  удалили стратегию \ мастер портфель и запись с master_portfolio_position_retention
        confirmDeletedParams(true, true, true);
    }

    @Test
    @AllureId("1739136")
    @DisplayName("C1739136.deleteDraftStrategy. Не удаляем стратегию в статусе active")
    @Subfeature("Успешные сценарии")
    @Description("Удаление draft-стратегии администратором")
    void C1739136() {
        strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        stpTrackingAdminSteps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investIdMaster, null, contractIdMaster, ContractState.untracked,
            strategyId, stpTrackingAdminSteps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 3, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = new ArrayList<>();
        stpTrackingAdminSteps.createMasterPortfolio(contractIdMaster, strategyId,1,"3000", masterPos);
        createDateMasterPortfolioPositionRetention(strategyId, 10, 2, "days");
        //Вызываем метод deleteDraftStrategy
        deleteDraftStrategy(strategyId);
        //Проверяем, что Не удалили стратегию \ мастер портфель и запись с master_portfolio_position_retention
       confirmDeletedParams(false, false, false);
    }

    @Test
    @AllureId("1739141")
    @DisplayName("C1739141.deleteDraftStrategy. Не нашли стратегию по strategy_id")
    @Subfeature("Успешные сценарии")
    @Description("Удаление draft-стратегии администратором")
    void C1739141() {
        strategyId = UUID.randomUUID();
        //Создаем клиента контракт и стратегию в БД tracking: client, contract, strategy в статусе active
        stpTrackingAdminSteps.createClientWithContractAndStrategy(siebel.siebelIdAdmin, investIdMaster, null, contractIdMaster, ContractState.untracked,
            strategyId, stpTrackingAdminSteps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 3, expectedRelativeYield, "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);
        strategyService.deleteStrategy(strategyService.getStrategy(strategyId));
        // создаем портфель для master в cassandra
        List<MasterPortfolio.Position> masterPos = new ArrayList<>();
        stpTrackingAdminSteps.createMasterPortfolio(contractIdMaster, strategyId,1,"3000", masterPos);
        createDateMasterPortfolioPositionRetention(strategyId, 10, 2, "days");
        //Вызываем метод deleteDraftStrategy
        deleteDraftStrategy(strategyId);
        //Проверяем, что Не удалили стратегию \ мастер портфель и запись с master_portfolio_position_retention
        confirmDeletedParams(true, false, false);
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

    void deleteDraftStrategy (UUID strategyId) {
        strategyApiStrategyApiAdminCreator.get().deleteDraftStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .strategyIdPath(strategyId.toString())
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
    }

    void confirmDeletedParams (Boolean strategyWasDeleted, Boolean isMasterPortfolioWasDeleted, Boolean masterPortfolioPositionRetentionWasDeleted){
        await().pollDelay(Duration.ofSeconds(2)).pollInterval(Duration.ofNanos(200)).atMost(Duration.ofSeconds(4)).until(() ->
            masterPortfolioPositionRetentionDao.getListMasterPortfolioPositionRetention(strategyId).isEmpty(), is(masterPortfolioPositionRetentionWasDeleted));
        assertThat("Не удалили записи strategy", strategyService.findListStrategyByContractId(contractIdMaster).isEmpty(), is(strategyWasDeleted));
        assertThat("Не удалили записи master_portfolio", masterPortfolioDao.findLatestMasterPortfolio(contractIdMaster, strategyId).isEmpty(), is(isMasterPortfolioWasDeleted));
        assertThat("Не удалили записи masterPortfolioPositionRetention", masterPortfolioPositionRetentionDao.getListMasterPortfolioPositionRetention(strategyId).isEmpty(), is(masterPortfolioPositionRetentionWasDeleted));
    }
}
