package stpTrackingApi.updateStrategy;


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
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.StrategyBaseCurrency;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Epic("updateStrategy - Обновление параметров стратегии ведущим")
@Feature("TAP-6784")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class})
public class UpdateStrategySuccessTest {
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
    Client client;
    Contract contract;
    Strategy strategy;
    Profile profile;
    String SIEBEL_ID = "5-10KF9CLVT";

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                trackingService.deleteStrategy(strategy);
            } catch (Exception e) {}
            try {
                сontractService.deleteContract(contract);
            } catch (Exception e) {}
            try {
                clientService.deleteClient(client);
            } catch (Exception e) {}
        });
    }

    @Test
    @AllureId("542525")
    @DisplayName("C542525.UpdateStrategy.Успешное обновление стратегии, все параметры")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C542525() {
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        UUID strategyId = UUID.randomUUID();
        String title = "Тест стратегия автотестов 01";
        String description = "Тестовая стратегия для работы автотестов 01";
        //находим клиента в social и берем данные по профайлу
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //находим investId клиента в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        String contractId = findValidAccountWithSiebleId.get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, socialProfile, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
        request.setDescription(description);
        // вызываем метод updateStrategy()
        Response exerep = strategyApi.updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем Headers ответа
        assertFalse(exerep.getHeaders().getValue("x-trace-id").isEmpty());
//        assertThat("x-server-time не равно", exerep.getHeaders().getValue("x-server-time").substring(0, 16), is(dateNow));
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("542526")
    @DisplayName("C542526.GetUntrackedContracts.Успешное обновление стратегии, параметры, которые НЕ переданы в запросе, оставляем без изменений")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C542526() {
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        UUID strategyId = UUID.randomUUID();
        String title = "Тест стратегия автотестов 01";
        String description = "Тестовая стратегия для работы автотестов";
        //находим клиента в social и берем данные по профайлу
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //находим investId клиента в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        String contractId = findValidAccountWithSiebleId.get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, socialProfile, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
        // вызываем метод updateStrategy()
        Response exerep = strategyApi.updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем Headers ответа
        assertFalse(exerep.getHeaders().getValue("x-trace-id").isEmpty());
//        assertThat("x-server-time не равно", exerep.getHeaders().getValue("x-server-time").substring(0, 16), is(dateNow));
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is(title));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    @Test
    @AllureId("542527")
    @DisplayName("C542527.UpdateStrategy.Успешное обновление стратегии, для title удаляем все пробелы в начале и в конце значения")
    @Subfeature("Успешные сценарии")
    @Description("Метод позволяет ведущему (автору стратегии) обновить параметры стратегии до ее публикации")
    void C542527() {
        //получаем текущую дату и время
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String dateNow = (fmt.format(now));
        UUID strategyId = UUID.randomUUID();
        String title = "  Тест стратегия автотестов 01    ";
        String description = "Тестовая стратегия для работы автотестов 01";
        //находим клиента в social и берем данные по профайлу
        profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString());
        //находим investId клиента в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleId = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID);
        UUID investId = findValidAccountWithSiebleId.get(0).getInvestAccount().getId();
        String contractId = findValidAccountWithSiebleId.get(0).getId();
        //создаем клиента со стратегией в статусе неактивная
        createClientWintContractAndStrategyMulti(investId, ClientStatusType.registered, socialProfile, contractId, strategyId, null, ContractState.untracked,
            StrategyCurrency.rub, StrategyRiskProfile.conservative, StrategyStatus.draft, null);
        //формируем тело запроса
        ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest request = new ru.qa.tinkoff.swagger.tracking.model.UpdateStrategyRequest();
        request.setTitle(title);
        request.setDescription(description);
        // вызываем метод updateStrategy()
        Response exerep = strategyApi.updateStrategy()
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .body(request)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //Проверяем Headers ответа
        assertFalse(exerep.getHeaders().getValue("x-trace-id").isEmpty());
//        assertThat("x-server-time не равно", exerep.getHeaders().getValue("x-server-time").substring(0, 16), is(dateNow));
        //находим в БД автоследования стратегию и проверяем ее поля
        strategy = strategyService.getStrategy(strategyId);
        assertThat("номера стратегии не равно", strategy.getId(), is(strategyId));
        assertThat("номера договора клиента не равно", strategy.getContract().getId(), is(contractId));
        assertThat("название стратегии не равно", (strategy.getTitle()), is("Тест стратегия автотестов 01"));
        assertThat("валюта стратегии не равно", (strategy.getBaseCurrency()).toString(), is(StrategyBaseCurrency.RUB.toString()));
        assertThat("описание стратегии не равно", strategy.getDescription(), is(description));
        assertThat("статус стратегии не равно", strategy.getStatus().toString(), is("draft"));
        assertThat("риск-профиль стратегии не равно", (strategy.getRiskProfile()).toString(), is(ru.qa.tinkoff.swagger.tracking.model.StrategyRiskProfile.CONSERVATIVE.toString()));
    }


    //***методы для работы тестов**************************************************************************
    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWintContractAndStrategyMulti(UUID investId, ClientStatusType сlientStatusType, SocialProfile socialProfile, String contractId, UUID strategyId, ContractRole contractRole,
                                                  ContractState contractState, StrategyCurrency strategyCurrency,
                                                  StrategyRiskProfile strategyRiskProfile, StrategyStatus strategyStatus, LocalDateTime date) {
        client = clientService.createClient(investId, сlientStatusType, socialProfile);
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);

        contract = сontractService.saveContract(contract);

        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contract)
            .setTitle("Тест стратегия автотестов")
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription("Тестовая стратегия для работы автотестов")
            .setStatus(strategyStatus)
            .setSlavesCount(0)
            .setActivationTime(date)
            .setScore(1);

        strategy = trackingService.saveStrategy(strategy);
    }
}
