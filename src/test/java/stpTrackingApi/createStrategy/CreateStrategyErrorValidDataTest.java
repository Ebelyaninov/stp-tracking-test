package stpTrackingApi.createStrategy;

import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import org.json.JSONObject;
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
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.CreateStrategyRequest;
import ru.qa.tinkoff.swagger.tracking.model.Currency;
import ru.qa.tinkoff.swagger.tracking.model.StrategyFeeRate;
import ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("createStrategy - Создание стратегии")
@Feature("TAP-6805")
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    KafkaAutoConfiguration.class
})
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
            try {
                сontractService.deleteContract(contract);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(client);
            } catch (Exception e) {
            }
        });
    }

    @SneakyThrows
    @Test
    @AllureId("435867")
    @DisplayName("C435867.CreateStrategy.Валидация запроса: title > 30 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C435867() {
        String title = "общий, недетализированный план.";
        String description = "new test стратегия autotest CreateStrategy007";
        StrategyFeeRate feeRate = new StrategyFeeRate();
        feeRate.setManagement(0.04);
        feeRate.setResult(0.2);
        //Находим investId клиента через API сервиса счетов
        GetBrokerAccountsResponse brokerAccount = getBrokerAccountByAccountPublicApi(SIEBEL_ID);
        UUID investId = brokerAccount.getInvestId();
        String contractId = brokerAccount.getBrokerAccounts().get(0).getId();
        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);
        //Формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("4000.0");
        CreateStrategyRequest request = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, baseMoney, "days", feeRate);
        //Вызываем метод CreateStrategy
        StrategyApi.CreateStrategyOper createStrategy = strategyApi.createStrategy()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios 8.1")
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(422));
        createStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(createStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Некорректное название для стратегии"));
    }

    @Test
    @AllureId("1138040")
    @DisplayName("С1138040.CreateStrategy. Валидация запроса: отсутствие параметра description")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C1138040() {

        String title = "Autotest 004";
        String positionRetentionId = "days";
        StrategyFeeRate feeRate = new StrategyFeeRate();
        feeRate.setManagement(0.04);
        feeRate.setResult(0.2);
        //Находим investId клиента через API сервиса счетов
        GetBrokerAccountsResponse brokerAccount = getBrokerAccountByAccountPublicApi(SIEBEL_ID);
        UUID investId = brokerAccount.getInvestId();
        String contractId = brokerAccount.getBrokerAccounts().get(0).getId();
        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);
        //Формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("3000.0");
        CreateStrategyRequest request = new CreateStrategyRequest();
        request.setContractId(contractId);
        request.setBaseCurrency(ru.qa.tinkoff.swagger.tracking.model.Currency.RUB);
        request.setRiskProfile(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE);
        request.setTitle(title);
        request.setBaseMoneyPositionQuantity(baseMoney);
        request.setPositionRetentionId(positionRetentionId);
        request.setFeeRate(feeRate);
        //Вызываем метод CreateStrategy
        Response expectedResponse = strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);

        assertThat("номера стратегии не равно", expectedResponse.getBody().jsonPath().get("errorCode"), is("Error"));
        assertThat("номера стратегии не равно", expectedResponse.getBody().jsonPath().get("errorMessage"), is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("435886")
    @DisplayName("C435886.CreateStrategy.Создание стратегии со значением description > 500 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод создания стратегии на договоре ведущего")
    void C435886() {
        String title = "тест стратегия06";
        String description = "Страте́гия (др.-греч. — искусство полководца) — общий, недетализированный план, " +
            "охватывающий длительный период времени, способ достижения сложной цели, позднее вообще какой-либо " +
            "деятельности человека. Задачей стратегии является эффективное использование наличных ресурсов " +
            "для достижения основной цели (стратегия как способ действий становится особо необходимой в " +
            "ситуации, когда для прямого достижения основной цели недостаточно наличных ресурсов). " +
            "Понятие произошло от понятия военная стратегия — наука о ведении войны, одна из областей военного искусства, " +
            "высшее его проявление, которое охватывает вопросы теории и практики подготовки к войне, её планирование " +
            "и ведение, исследует закономерности войны.";
        StrategyFeeRate feeRate = new StrategyFeeRate();
        feeRate.setManagement(0.04);
        feeRate.setResult(0.2);
        //Находим investId клиента через API сервиса счетов
        GetBrokerAccountsResponse brokerAccount = getBrokerAccountByAccountPublicApi(SIEBEL_ID);
        UUID investId = brokerAccount.getInvestId();
        String contractId = brokerAccount.getBrokerAccounts().get(0).getId();
        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.registered, null);
        //Формируем тело запроса
        BigDecimal baseMoney = new BigDecimal("6000.0");
        CreateStrategyRequest request = createStrategyRequest (Currency.RUB, contractId, description,
            StrategyRiskProfile.CONSERVATIVE, title, baseMoney, "days", feeRate);
        //Вызываем метод CreateStrategy
        Response expectedResponse = strategyApi.createStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(500))
            .execute(response -> response);
        //Проверяем мета-данные response, x-trace-id  x-server-time не пустые значения
        assertFalse(expectedResponse.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(expectedResponse.getHeaders().getValue("x-server-time").isEmpty());
        //Находим в БД автоследования созданный контракт и Проверяем его поля
        contract = сontractService.getContract(contractId);
        assertThat("номера договоров не равно", contract.getId(), is(contractId));
        assertThat("роль клиента не равно null", (contract.getRole()), is(nullValue()));
        assertThat("статус клиента не равно", (contract.getState()).toString(), is("untracked"));
        assertThat("стратегия не равно null", (contract.getStrategyId()), is(nullValue()));
        Optional<Strategy> strategyOpt = strategyService.findStrategyByContractId(contractId);
        assertThat("запись по стратегии не равно", strategyOpt.isPresent(), is(false));
    }


    // *** Методы для работы тестов ***
    //Метод находит подходящий siebelId в сервисе счетов и Создаем запись по нему в табл. tracking.client
    void createClient(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile) {
        client = clientService.createClient(investId, clientStatusType, socialProfile);
    }

    //Метод для получения инфо о клиенте через API - сервиса счетов
    @Step("Получение инфо об аккаунте клиента через API сервиса счетов")
    GetBrokerAccountsResponse getBrokerAccountByAccountPublicApi(String siebelId) {
        GetBrokerAccountsResponse resBrokerAccount = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(siebelId)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .isBlockedQuery("false")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        return resBrokerAccount;
    }


    CreateStrategyRequest createStrategyRequest (Currency currency, String contractId, String description,
                                                 StrategyRiskProfile strategyRiskProfile, String title,
                                                 BigDecimal basemoney, String  positionRetentionId,
                                                 StrategyFeeRate feeRate) {
        CreateStrategyRequest createStrategyRequest = new CreateStrategyRequest();
        createStrategyRequest.setBaseCurrency(currency);
        createStrategyRequest.setContractId(contractId);
        createStrategyRequest.setDescription(description);
        createStrategyRequest.setRiskProfile(strategyRiskProfile);
        createStrategyRequest.setTitle(title);
        createStrategyRequest.setBaseMoneyPositionQuantity(basemoney);
        createStrategyRequest.setPositionRetentionId(positionRetentionId);
        createStrategyRequest.setFeeRate(feeRate);
        return createStrategyRequest;
    }
}
