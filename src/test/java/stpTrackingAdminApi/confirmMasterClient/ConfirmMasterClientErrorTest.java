package stpTrackingAdminApi.confirmMasterClient;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
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
import ru.qa.tinkoff.creator.adminCreator.ApiAdminCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.ClientApi;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.services.database.ClientService;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Epic("confirmMasterClient - Подтверждение ведущего")
@Feature("TAP-6419")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("confirmMasterClient")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    AdminApiCreatorConfiguration.class,
    ApiCreatorConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
})


public class ConfirmMasterClientErrorTest {
    @Autowired
    ProfileService profileService;
    @Autowired
    ClientService clientService;
    @Autowired
    StpSiebel siebel;
    @Autowired
    ApiAdminCreator<ClientApi> clientApiAdminCreator;
    @Autowired
    InvestAccountCreator<BrokerAccountApi> brokerAccountApiCreator;
    @Autowired
    StpTrackingAdminSteps adminSteps;

    String xApiKey = "x-api-key";
    String keyRead = "tcrm";
    UUID investId;
    String contractId;


    @BeforeAll
    void deleteClient() {
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdAdmin)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        adminSteps.deleteDataFromDb(siebel.siebelIdAdmin);
    }

    @BeforeEach
    void getDataFromAccount (){
        try {
            clientService.deleteClientById(investId);
        } catch (Exception e) {
        }
    }

    private static Stream<Arguments> provideStringsForHeadersConfirmMasterClient() {
        return Stream.of(
            Arguments.of(null, "tracking_admin"),
            Arguments.of("trading-invest", null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideStringsForHeadersConfirmMasterClient")
    @AllureId("455794")
    @DisplayName("C455794.ConfirmMasterClient.Валидация запроса: передача обязательных параметров")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C455794(String name, String login) {
        //вызываем метод confirmMasterClient
        ClientApi.ConfirmMasterClientOper confirmMasterClient = clientApiAdminCreator.get().confirmMasterClient()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .clientIdPath(investId)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            confirmMasterClient = confirmMasterClient.xAppNameHeader(name);
        }
        if (login != null) {
            confirmMasterClient = confirmMasterClient.xTcsLoginHeader(login);
        }
        confirmMasterClient.execute(ResponseBodyData::asString);
        //проверяем, что запись о клиенте не появилась в tracking.client
        Optional<Client> clientOpt = clientService.findClient(investId);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("263126")
    @DisplayName("C263126.ConfirmMasterClient.Валидация запроса: Невалидное значение сlientId (не UUID)")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C263126() {
        //вызываем метод confirmMasterClient с невалидным значением clientId (не UUID)
        clientApiAdminCreator.get().confirmMasterClient()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .clientIdPath("8f8da33d96c1445880eab27d8e91b976")
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response.asString());
    }

    @Test
    @AllureId("455854")
    @DisplayName("C455854.ConfirmMasterClient.Валидация запроса: значение X-TCS-LOGIN > 20 символов")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C455854() {
        //вызываем метод confirmMasterClient со значением Login > 20 символов
        clientApiAdminCreator.get().confirmMasterClient()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admintracking_admin1232422353456")
            .clientIdPath(investId)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response.asString());
        Optional<Client> clientOpt = clientService.findClient(investId);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("467544")
    @DisplayName("C467544.ConfirmMasterClient.Авторизация: не передаем X-API-KEY")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C467544() {
        clientApiAdminCreator.get().confirmMasterClient()
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .clientIdPath(investId)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
        Optional<Client> clientOpt = clientService.findClient(investId);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("455861")
    @DisplayName("C455861.ConfirmMasterClient.Авторизация: Неверное значение X-API-KEY")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C455861() {
        //вызываем метод confirmMasterClient с неверным значением api-key
        clientApiAdminCreator.get().confirmMasterClient()
            .reqSpec(r -> r.addHeader(xApiKey, "trackidngc"))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .clientIdPath(investId)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
        Optional<Client> clientOpt = clientService.findClient(investId);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
    }


    @Test
    @AllureId("1705432")
    @DisplayName("C1705432.ConfirmMasterClient.Авторизация: Значение X-API-KEYс доступом read")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C1705432() {
        //вызываем метод confirmMasterClient с неверным значением api-key
        clientApiAdminCreator.get().confirmMasterClient()
            .reqSpec(r -> r.addHeader(xApiKey, keyRead))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .clientIdPath(investId)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.asString());
        Optional<Client> clientOpt = clientService.findClient(investId);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("263127")
    @DisplayName("C263127.ConfirmMasterClient.Проверяем, что у клиента есть инвестиционный счет: несуществующие значение сlientId")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C263127() {
        UUID invest_id = UUID.fromString("f45bfa77-3f63-4c1d-a7fb-8ee863333933");
        //вызываем метод confirmMasterClient с несуществующим значением clientId
        clientApiAdminCreator.get().confirmMasterClient()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .clientIdPath(invest_id)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response.asString());
        Optional<Client> clientOpt = clientService.findClient(invest_id);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
    }

    @Test
    @AllureId("455922")
    @DisplayName("C455922.ConfirmMasterClient.Подтягиваем данные профиля клиента в Пульсе : клиент без записи в БД social")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C455922() {
        UUID invest_id = UUID.fromString("f749bb39-df42-4469-94d3-5d503531d1b7");
        //вызываем метод confirmMasterClient
        clientApiAdminCreator.get().confirmMasterClient()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .clientIdPath(invest_id)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response.asString());
        Optional<Client> clientOpt = clientService.findClient(invest_id);
        assertThat("запись по клиенту не равно", clientOpt.isPresent(), is(false));
    }
}