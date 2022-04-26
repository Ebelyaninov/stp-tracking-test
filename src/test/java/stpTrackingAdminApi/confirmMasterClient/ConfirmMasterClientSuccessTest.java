package stpTrackingAdminApi.confirmMasterClient;


import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
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
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.ClientApi;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;

import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    InvestTrackingAutoConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    AdminApiCreatorConfiguration.class,
    ApiCreatorConfiguration.class,
    StpTrackingAdminStepsConfiguration.class
})

public class ConfirmMasterClientSuccessTest {
    Client client;
    String xApiKey = "x-api-key";
    @Autowired
    ProfileService profileService;
    @Autowired
    ClientService clientService;
    @Autowired
    ApiAdminCreator<ClientApi> clientApiAdminCreator;
    @Autowired
    StpSiebel siebel;
    @Autowired
    InvestAccountCreator<BrokerAccountApi> brokerAccountApiCreator;
    @Autowired
    ContractService contractService;
    @Autowired
    StpTrackingAdminSteps adminSteps;

    UUID investId;
    String contractId;

    @BeforeAll
    void deleteClientAndContract() {
        step("Удаляем клиента автоследования", () -> {
            GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
                .siebelIdPath(siebel.siebelIdAdmin)
                .brokerTypeQuery("broker")
                .brokerStatusQuery("opened")
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(GetBrokerAccountsResponse.class));
            investId = resAccountMaster.getInvestId();
            contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
            adminSteps.deleteDataFromDb(contractId, investId);
        });
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            clientService.deleteClient(client);
        });
    }


    @Test
    @AllureId("259274")
    @DisplayName("C259274.ConfirmMasterClient.Успешное подтверждение ведущего - клиент ранее не регистровался")
    @Subfeature("Успешные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C259274() {
        //находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(siebel.siebelIdAdmin);
        //получаем данные по клиенту  в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
//            .siebelIdPath(siebel.siebelIdAdmin)
//            .brokerTypeQuery("broker")
//            .brokerStatusQuery("opened")
//            .respSpec(spec -> spec.expectStatusCode(200))
//            .execute(response -> response.as(GetBrokerAccountsResponse.class));
//        UUID investId = resAccountMaster.getInvestId();
        //вызываем метод confirmMasterClient
        Response responseConfirmMaster = clientApiAdminCreator.get().confirmMasterClient()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .clientIdPath(investId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-server-time").isEmpty());
        client = clientService.getClient(investId);
        assertThat("номера договоров не равно", client.getId(), is(investId));
        assertThat("номера клиента не равно", client.getMasterStatus().toString(), is("confirmed"));
    }


    @Test
    @AllureId("261414")
    @DisplayName("C261414.ConfirmMasterClient.Успешное подтверждение ведущего master_status ='registered'")
    @Subfeature("Успешные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C261414() {
        //находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(siebel.siebelIdAdmin);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
//            .setImage(profile.getImage().toString())
            ;
        //получаем данные по клиенту  в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
//            .siebelIdPath(siebel.siebelIdAdmin)
//            .brokerTypeQuery("broker")
//            .brokerStatusQuery("opened")
//            .respSpec(spec -> spec.expectStatusCode(200))
//            .execute(response -> response.as(GetBrokerAccountsResponse.class));
//        UUID investId = resAccountMaster.getInvestId();
        createClient(investId, ClientStatusType.registered, socialProfile);
        //вызываем метод confirmMasterClient
        Response responseConfirmMaster = clientApiAdminCreator.get().confirmMasterClient()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .clientIdPath(investId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-server-time").isEmpty());
        //проверяем, что запись в tracking.client со статусом на registered
        client = clientService.getClient(investId);
        assertThat("номера договоров не равно", client.getId(), is(investId));
        assertThat("номера клиента не равно", client.getMasterStatus().toString(), is("registered"));
    }

    @Test
    @AllureId("261568")
    @DisplayName("C261568.ConfirmMasterClient.Успешное подтверждение ведущего master_status ='confirmed'")
    @Subfeature("Успешные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C261568() {
        //находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(siebel.siebelIdAdmin);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
//            .setImage(profile.getImage().toString())
            ;
        //получаем данные по клиенту  в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
//            .siebelIdPath(siebel.siebelIdAdmin)
//            .brokerTypeQuery("broker")
//            .brokerStatusQuery("opened")
//            .respSpec(spec -> spec.expectStatusCode(200))
//            .execute(response -> response.as(GetBrokerAccountsResponse.class));
//        UUID investId = resAccountMaster.getInvestId();
//        //добавляем запись в tracking.client со статусом confirmed
        createClient(investId, ClientStatusType.confirmed, socialProfile);
        //вызываем метод confirmMasterClient
        Response responseConfirmMaster = clientApiAdminCreator.get().confirmMasterClient()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .clientIdPath(investId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-server-time").isEmpty());
        //проверяем, что запись в tracking.client со статусом на confirmed
        client = clientService.getClient(investId);
        assertThat("номера договоров не равно", client.getId(), is(investId));
        assertThat("номера клиента не равно", client.getMasterStatus().toString(), is("confirmed"));
    }

    @Test
    @AllureId("261734")
    @DisplayName("C261734.ConfirmMasterClient.Успешное подтверждение ведущего master_status ='none'")
    @Subfeature("Успешные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C261734() {
        //находим клиента в БД social
        Profile profile = profileService.getProfileBySiebelId(siebel.siebelIdAdmin);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
//            .setImage(profile.getImage().toString())
            ;
        //получаем данные по клиенту  в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
//            .siebelIdPath(siebel.siebelIdAdmin)
//            .brokerTypeQuery("broker")
//            .brokerStatusQuery("opened")
//            .respSpec(spec -> spec.expectStatusCode(200))
//            .execute(response -> response.as(GetBrokerAccountsResponse.class));
//        UUID investId = resAccountMaster.getInvestId();
        //добавляем запись в tracking.client со статусом confirmed
        createClient(investId, ClientStatusType.confirmed, socialProfile);
        //вызываем метод confirmMasterClient
        Response responseConfirmMaster = clientApiAdminCreator.get().confirmMasterClient()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .clientIdPath(investId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-server-time").isEmpty());
        //проверяем, что запись в tracking.client изменила статус на confirmed
        client = clientService.getClient(investId);
        assertThat("номера договоров не равно", client.getId(), is(investId));
        assertThat("номера клиента не равно", client.getMasterStatus().toString(), is("confirmed"));
    }


    @Test
    @AllureId("455943")
    @DisplayName("C455943.ConfirmMasterClient.Подтягиваем данные профиля клиента в Пульсе: пустое значение nickname")
    @Subfeature("Успешные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C455943() {
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdEmptyNick)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdEmptyNick = resAccountMaster.getInvestId();
        //находим запись в БД social с пустым значением в поле nickname
        //достаем из БД сервиса счетов sieble_id, у которого несколько инвест счетов
        Profile profile = profileService.getProfileBySiebelId(siebel.siebelIdEmptyNick);
        //вызываем метод confirmMasterClient
        Response responseConfirmMaster = clientApiAdminCreator.get().confirmMasterClient()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .clientIdPath(investIdEmptyNick)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-server-time").isEmpty());
        //проверяем, что запись в tracking.client изменила статус на confirmed
        client = clientService.getClient(investIdEmptyNick);
        assertThat("номера договоров не равно", client.getId(), is(investIdEmptyNick));
        assertThat("номера клиента не равно", client.getMasterStatus().toString(), is("confirmed"));
        assertThat("идентификатор профайла не равно", (client.getSocialProfile().getId()), is(profile.getId().toString()));
        assertThat("Nickname клиента не равно", (client.getSocialProfile().getNickname()), is(profile.getNickname()));
//        assertThat("Image клиента не равно",  (client.getSocialProfile().getImage()), is(profile.getImage().toString()));
    }


    @Test
    @AllureId("455952")
    @DisplayName("C455952.ConfirmMasterClient.Подтягиваем данные профиля клиента в Пульсе: пустое значение image")
    @Subfeature("Успешные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C455952() {
        //находим запись в БД social c с пустым значением в поле nickname
        Profile profile = profileService.getProfileBySiebelId(siebel.siebelIdNullImage);
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdNullImage)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdNullImage = resAccountMaster.getInvestId();
        //вызываем метод confirmMasterClient
        Response responseConfirmMaster = clientApiAdminCreator.get().confirmMasterClient()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .clientIdPath(investIdNullImage)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-server-time").isEmpty());
        //проверяем, что запись в tracking.client изменила статус на confirmed
        client = clientService.getClient(investIdNullImage);
        assertThat("номера договоров не равно", client.getId(), is(investIdNullImage));
        assertThat("номера клиента не равно", client.getMasterStatus().toString(), is("confirmed"));
        assertThat("идентификатор профайла не равно", (client.getSocialProfile().getId()), is(profile.getId().toString()));
        assertThat("Nickname клиента не равно", (client.getSocialProfile().getNickname()), is(profile.getNickname()));
        assertThat("Image клиента не равно", (client.getSocialProfile().getImage().toString()), is(profile.getImage().toString()));
    }


    @Test
    @AllureId("455965")
    @DisplayName("C455965.ConfirmMasterClient.Подтягиваем данные профиля клиента в Пульсе: не пустое значение image и nickname")
    @Subfeature("Успешные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C455965() {
        //находим запись в БД social c с пустым значением в поле nickname
        Profile profile = profileService.getProfileBySiebelId(siebel.siebelIdAdmin);
        //находим данные клиента в сервисе счетов: договор БС, investId
        //получаем данные по клиенту  в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
//            .siebelIdPath(siebel.siebelIdAdmin)
//            .brokerTypeQuery("broker")
//            .brokerStatusQuery("opened")
//            .respSpec(spec -> spec.expectStatusCode(200))
//            .execute(response -> response.as(GetBrokerAccountsResponse.class));
//        UUID investId = resAccountMaster.getInvestId();
        //вызываем метод confirmMasterClient
        Response responseConfirmMaster = clientApiAdminCreator.get().confirmMasterClient()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .clientIdPath(investId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-server-time").isEmpty());
        //проверяем, что запись в tracking.client изменила статус на confirmed
        client = clientService.getClient(investId);
        assertThat("номера договоров не равно", client.getId(), is(investId));
        assertThat("номера клиента не равно", client.getMasterStatus().toString(), is("confirmed"));
        assertThat("идентификатор профайла не равно", (client.getSocialProfile().getId()), is(profile.getId().toString()));
        assertThat("Nickname клиента не равно", (client.getSocialProfile().getNickname()), is(profile.getNickname()));
//        assertThat("Image клиента не равно",  (client.getSocialProfile().getImage()), is(profile.getImage().toString()));
    }

    @Test
    @AllureId("455898")
    @DisplayName("C455898.ConfirmMasterClient.Проверяем, что у клиента есть инвестиционный счет: значение clientId, у которого Type !=broker")
    @Subfeature("Успешные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C455898() {
        //находим данные клиента в сервисе счетов: договор БС, investId
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdNotBroker)
            .brokerTypeQuery("invest-box")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdNotBroker = resAccountMaster.getInvestId();
        Profile profile = profileService.getProfileBySiebelId(siebel.siebelIdNotBroker);
        //вызываем метод confirmMasterClient
        Response responseConfirmMaster = clientApiAdminCreator.get().confirmMasterClient()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .clientIdPath(investIdNotBroker)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-server-time").isEmpty());
        //проверяем, что запись в tracking.client изменила статус на confirmed
        client = clientService.getClient(investIdNotBroker);
        assertThat("номера договоров не равно", client.getId(), is(investIdNotBroker));
        assertThat("номера клиента не равно", client.getMasterStatus().toString(), is("confirmed"));
        assertThat("идентификатор профайла не равно", (client.getSocialProfile().getId()), is(profile.getId().toString()));
        assertThat("Nickname клиента не равно", (client.getSocialProfile().getNickname()), is(profile.getNickname()));
    }

    @Test
    @AllureId("455990")
    @DisplayName("C455990.ConfirmMasterClient.Проверяем, что у клиента есть инвестиционный счет: 1 БС со статусом = closed")
    @Subfeature("Успешные сценарии")
    @Description("Метод для администратора для подтверждения клиенту статуса ведущего")
    void C455990() {
        //получаем данные по клиенту  в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = brokerAccountApiCreator.get().getBrokerAccountsBySiebel()
            .siebelIdPath(siebel.siebelIdNotOpen)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("closed")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        UUID investIdNotOpen = resAccountMaster.getInvestId();
        //вызываем метод confirmMasterClient
        Response responseConfirmMaster = clientApiAdminCreator.get().confirmMasterClient()
            .reqSpec(r -> r.addHeader(xApiKey, "tracking"))
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("android")
            .xDeviceIdHeader("test")
            .xTcsLoginHeader("tracking_admin")
            .clientIdPath(investIdNotOpen)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        //проверяем, что в response есть заголовки x-trace-id и x-server-time
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-trace-id").isEmpty());
        assertFalse(responseConfirmMaster.getHeaders().getValue("x-server-time").isEmpty());
        //проверяем, что запись в tracking.client изменила статус на confirmed
        client = clientService.getClient(investIdNotOpen);
        assertThat("номера договоров не равно", client.getId(), is(investIdNotOpen));
        assertThat("номера клиента не равно", client.getMasterStatus().toString(), is("confirmed"));
    }

/////////***методы для работы тестов**************************************************************************

    //метод находит подходящий siebleId в сервисе счетов и создаем запись по нему в табл. tracking.client
    void createClient(UUID clientId, ClientStatusType сlientStatusType, SocialProfile socialProfile) {
        client = clientService.createClient(clientId, сlientStatusType, socialProfile, null);
    }
}
