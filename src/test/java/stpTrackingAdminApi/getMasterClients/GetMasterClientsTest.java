package stpTrackingAdminApi.getMasterClients;

import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiAdminCreator;
import ru.qa.tinkoff.creator.ClientApiAdminCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.ClientApi;
import ru.qa.tinkoff.swagger.tracking_admin.model.GetMasterClientsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Epic("getMasterClients - Получение списка ведущих")
@Feature("TAP-10786")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("getMasterClients")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    ClientApiAdminCreator.class
})
public class GetMasterClientsTest {
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StpTrackingAdminSteps stpTrackingAdminSteps;
    @Autowired
    StrategyService strategyService;
    @Autowired
    MasterSignalDao masterSignalDao;
    @Autowired
    StpInstrument instrument;
    @Autowired
    ApiAdminCreator<ClientApi> clientApiAdminCreator;
    @Autowired
    StpTrackingAdminSteps steps;
    String xApiKey = "x-api-key";
    String key = "tracking";
    String keyRead = "tcrm";
    Client client;


    String siebelIdMaster = "5-DYNN1E3S";
    String contractIdMaster;
    UUID strategyId;
    LocalDateTime localDateTime;
    UUID investIdMaster;

    @BeforeAll
    void getDataFromAccount() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = stpTrackingAdminSteps.getBrokerAccounts(siebelIdMaster);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
    }


    @BeforeEach
    void createClient() {
        strategyId = UUID.randomUUID();
        localDateTime = LocalDateTime.now();
        int randomNumber = 0 + (int) (Math.random() * 1000);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        stpTrackingAdminSteps.createClientWithContractAndStrategyNew(siebelIdMaster, investIdMaster, ClientRiskProfile.aggressive, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(1));
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                masterSignalDao.deleteMasterSignalByStrategy(strategyId);
            } catch (Exception e) {
            }
            try {
                strategyService.deleteStrategy(stpTrackingAdminSteps.strategy);
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
        });
    }


    private static Stream<Arguments> provideRequiredParamLimit() {
        return Stream.of(
            Arguments.of(3, 3),
            Arguments.of(31, 31)
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @AllureId("1044637")
    @MethodSource("provideRequiredParamLimit")
    @DisplayName("C1044637.GetMasterClients.Передан limit")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка ведущих")
    void C1044637(int limit, int result) {
        GetMasterClientsResponse getMasterClients = clientApiAdminCreator.get().getMasterClients()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .limitQuery(limit)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterClientsResponse.class));
        assertThat("количество записей не равно лимиту: ", getMasterClients.getItems().size(), is(result));
    }


    @SneakyThrows
    @Test
    @AllureId("1044636")
    @DisplayName("C1044636.GetMasterClients.Получение списка ведущих без указания cursor & limit")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка сигналов на стратегии")
    void C1044636() {
        List<Client> getMasters = clientService.getFindClientByMaster(30);
        String position = getMasters.get(29).getPosition().toString();
        GetMasterClientsResponse masterClients = getMasterClientsResponse();
        for (int i = 0; i < masterClients.getItems().size(); i++) {
            assertThat("id master не совпадает", getMasters.get(i).getId(), is(masterClients.getItems().get(i).getId()));
            if (getMasters.get(i).getSocialProfile() != null) {
                assertThat("nickname не совпадает", getMasters.get(i).getSocialProfile().getNickname(),
                    is(masterClients.getItems().get(i).getSocialProfile().getNickname()));
            }
        }
        checkCursoreAndHasNextCursor(masterClients, position, true);
    }


    @SneakyThrows
    @Test
    @AllureId("1044697")
    @DisplayName("C1044697.GetMasterClients.Position > cursor")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка сигналов на стратегии")
    void C1044697() {
        client = clientService.getClient(investIdMaster);
        Integer position = client.getPosition();
        List<Client> getMasters = clientService.getFindListClientsByMasterByPositionAndLimit(position, 30);
        GetMasterClientsResponse getMasterClients = clientApiAdminCreator.get().getMasterClients()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .cursorQuery(position)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterClientsResponse.class));
        for (int i = 0; i < getMasterClients.getItems().size(); i++) {
            assertThat("id master не совпадает", getMasters.get(i).getId(), is(getMasterClients.getItems().get(i).getId()));
            if (getMasters.get(i).getSocialProfile() != null) {
                assertThat("nickname не совпадает", getMasters.get(i).getSocialProfile().getNickname(),
                    is(getMasterClients.getItems().get(i).getSocialProfile().getNickname()));
            }
        }
    }


    @SneakyThrows
    @Test
    @AllureId("1044698")
    @DisplayName("C1044698.GetMasterClients.Position < cursor")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка сигналов на стратегии")
    void C1044698() {
        List<Client> getMasters = clientService.getFindClientByMasterFirstPosition(30);
        Integer position = getMasters.get(0).getPosition();
        GetMasterClientsResponse getMasterClients = clientApiAdminCreator.get().getMasterClients()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .cursorQuery(position)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterClientsResponse.class));
        assertThat("количество записей не равно: ", getMasterClients.getItems().size(), is(0));
        checkCursoreAndHasNextCursor(getMasterClients, "null", false);
        assertThat("items != []", getMasterClients.getItems().toString(), is("[]"));
    }


    private static Stream<Arguments> provideStringsForHeadersGetMasterClients() {
        return Stream.of(
            Arguments.of(null, "tracking_admin"),
            Arguments.of("trading-invest", null)
        );
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideStringsForHeadersGetMasterClients")
    @AllureId("1044641")
    @DisplayName("C1044641.GetMasterClients.Валидация запроса: не передан обязательный заголовок")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C1044641(String name, String login) {
        //вызываем метод confirmMasterClient
        ClientApi.GetMasterClientsOper getMasterClients = clientApiAdminCreator.get().getMasterClients()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            getMasterClients = getMasterClients.xAppNameHeader(name);
        }
        if (login != null) {
            getMasterClients = getMasterClients.xTcsLoginHeader(login);
        }
        getMasterClients.execute(ResponseBodyData::asString);
    }


    @Test
    @AllureId("1707887")
    @DisplayName("C1707887.GetMasterClients.Авторизация: не передаем X-API-KEY")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C1707887() {
        //вызываем метод GetMasterClients без параметра api-key
        clientApiAdminCreator.get().getMasterClients()
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
    }


    @Test
    @AllureId("1707889")
    @DisplayName("C1707889.GetMasterClients.Авторизация: передан api-key с некорректным значением")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C1707889() {
        //вызываем метод GetMasterClients без параметра api-key
        clientApiAdminCreator.get().getMasterClients()
            .reqSpec(r -> r.addHeader(xApiKey, "trading"))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
    }

    @Test
    @AllureId("1706165")
    @DisplayName("C1706165.GetMasterClients.Авторизация: передан api-key с доступом read")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C1706165() {
        //вызываем метод GetMasterClients без параметра api-key
        clientApiAdminCreator.get().getMasterClients()
            .reqSpec(r -> r.addHeader(xApiKey, keyRead))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
    }


    @Step("Проверяем парамерты nextCursor и hasNext: ")
    void checkCursoreAndHasNextCursor(GetMasterClientsResponse getMasterClients, String nextCursor, Boolean hasNext) {
        if (nextCursor == "null") {
            nextCursor = null;
        }
        assertThat("nextCursor != " + nextCursor, getMasterClients.getNextCursor(),
            is(nextCursor));
        assertThat("hasNext != " + hasNext, getMasterClients.getHasNext(), is(hasNext));
    }


    @Step("Вызываем метод getMasterClients: ")
    GetMasterClientsResponse getMasterClientsResponse() {
        GetMasterClientsResponse getMasterClients = clientApiAdminCreator.get().getMasterClients()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking_admin")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetMasterClientsResponse.class));
        return getMasterClients;
    }
}
