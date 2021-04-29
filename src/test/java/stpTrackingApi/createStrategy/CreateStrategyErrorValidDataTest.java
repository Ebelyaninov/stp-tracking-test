package stpTrackingApi.createStrategy;


import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Epic("createStrategy - Создание стратегии")
@Feature("TAP-6805")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class})
public class CreateStrategyErrorValidDataTest {
    @Autowired
    BillingService billingService;
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService сontractService;
    @Autowired
    StrategyService strategyService;

    StrategyApi strategyApi = ApiClient.api(ApiClient.Config.apiConfig()).strategy();
    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    Client client;
    Contract contract;
    Profile profile;
    String SIEBEL_ID = "5-RGHKKZA6";

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            сontractService.deleteContract(contract);
            clientService.deleteClient(client);
        });
    }

    @Test
    @AllureId("435867")
    @DisplayName("C435867.CreateStrategy.Валидация запроса: title > 50 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C435867() {
        String title = "общий, недетализированный план, охватывающий длительный период времени, способ достижения сложной цели, позднее вообще какой-либо деятельности человека.";
        String description = "new test стратегия autotest CreateStrategy007";
        //находим клиента в social и берем данные по профайлу
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //находим investId клиента в БД сервиса счетов
//        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
//        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
//        String contractId = findValidAccountWithSiebleId.get(0).getId();
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, socialProfile);
        //формируем тело запроса
        BigDecimal basemoney = new BigDecimal("4000.0");
        ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.StrategyBaseCurrency.RUB);
        request.setDescription(description);
        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE);
        request.setTitle(title);
        request.setBaseMoneyPositionQuantity(basemoney);
        // вызываем метод CreateStrategy
        Response expectedResponse = strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(500))
            .execute(response -> response);
        //проверяем мета-данные response, x-trace-id  x-server-time не пустые значения
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //находим в БД автоследования созданный контракт и проверяем его поля
        contract = сontractService.getContract(contractId);
        assertThat("номера договоров не равно", contract.getId(), is(contractId));
        assertThat("роль клиента не равно null", (contract.getRole()), is(nullValue()));
        assertThat("статус клиента не равно", (contract.getState()).toString(), is("untracked"));
        assertThat("стратегия не равно null", (contract.getStrategyId()), is(nullValue()));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    @Test
    @AllureId("435886")
    @DisplayName("C435886.CreateStrategy.Создание стратегии со значением description > 500 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C435886() {
        String title = "тест стратегия06";
        String description = "Страте́гия (др.-греч. — искусство полководца) — общий, недетализированный план," +
            " охватывающий длительный период времени, способ достижения сложной цели, позднее вообще какой-либо " +
            "деятельности человека. Задачей стратегии является эффективное использование наличных ресурсов " +
            "для достижения основной цели (стратегия как способ действий становится особо необходимой в" +
            " ситуации, когда для прямого достижения основной цели недостаточно наличных ресурсов). " +
            "Понятие произошло от понятия военная стратегия — наука о ведении войны, одна из областей военного искусства," +
            " высшее его проявление, которое охватывает вопросы теории и практики подготовки к войне, её планирование" +
            " и ведение, исследует закономерности войны.";
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
//        //находим investId клиента в БД сервиса счетов
//        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebleId(SIEBEL_ID);
//        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
//        String contractId = findValidAccountWithSiebleId.get(0).getId();
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investId = resAccountMaster.getInvestId();
        String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();

        //создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, socialProfile);
        //формируем тело запроса
        BigDecimal basemoney = new BigDecimal("6000.0");
        ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.StrategyBaseCurrency.RUB);
        request.setDescription(description);
        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE);
        request.setTitle(title);
        request.setBaseMoneyPositionQuantity(basemoney);
        // вызываем метод CreateStrategy
        Response expectedResponse = strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(500))
            .execute(response -> response);
        //проверяем мета-данные response, x-trace-id  x-server-time не пустые значения
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //находим в БД автоследования созданный контракт и проверяем его поля
        contract = сontractService.getContract(contractId);
        assertThat("номера договоров не равно", contract.getId(), is(contractId));
        assertThat("роль клиента не равно null", (contract.getRole()), is(nullValue()));
        assertThat("статус клиента не равно", (contract.getState()).toString(), is("untracked"));
        assertThat("стратегия не равно null", (contract.getStrategyId()), is(nullValue()));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    /////////***методы для работы тестов**************************************************************************


    //метод находит подходящий siebleId в сервисе счетов и создаем запись по нему в табл. tracking.client
    void createClient(UUID investId, ClientStatusType сlientStatusType, SocialProfile socialProfile) {
        client = clientService.createClient(investId, сlientStatusType, socialProfile);
    }
}
