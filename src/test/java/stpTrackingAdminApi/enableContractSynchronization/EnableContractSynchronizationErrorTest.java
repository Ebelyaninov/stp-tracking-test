package stpTrackingAdminApi.enableContractSynchronization;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.AdminApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.ContractApiAdminCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.model.ErrorResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.util.UUID;

import static io.qameta.allure.Allure.step;

@Slf4j
@Epic("enableContractSynchronization - Включить синхронизацию позиций в slave-портфеле в обе стороны")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@Tags({@Tag("stp-tracking-admin"), @Tag("enableContractSynchronization")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    AdminApiCreatorConfiguration.class,
    ApiCreatorConfiguration.class
})

public class EnableContractSynchronizationErrorTest {


    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StpTrackingAdminSteps steps;
    @Autowired
    StpSiebel siebel;
    @Autowired
    ContractApiAdminCreator contractApiAdminCreator;

    String notContractIdSlave = "123456789";
    String longContractIdSlave = "123456789000000000";
    String contractIdSlave;
    UUID investIdSlave;
    String xApiKey = "x-api-key";
    String key = "tracking";
    String notKey = "counter";
    String keyRead = "tcrm";


    @BeforeAll
    void getDataClients() {
        //получаем данные по клиенту slave в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebel.siebelIdSlaveAdmin);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                contractService.deleteContract(contractService.getContract(contractIdSlave));
            } catch (Exception e) {
            }
        });
    }


    @SneakyThrows
    @Test
    @AllureId("1398796")
    @DisplayName("C1398796.enabledContractSynchronization. Передан некорректный x-api-key")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C1398796() {
        //вызываем метод enableContractSynchronization
        contractApiAdminCreator.get().enableContractSynchronization()
            .reqSpec(r -> r.addHeader(xApiKey, notKey))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking")
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response);
    }


    @SneakyThrows
    @Test
    @AllureId("1705484")
    @DisplayName("C1705484.enabledContractSynchronization. Передан некорректный x-api-key")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C1705484() {
        //вызываем метод enableContractSynchronization
        contractApiAdminCreator.get().enableContractSynchronization()
            .reqSpec(r -> r.addHeader(xApiKey, keyRead))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking")
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response);
    }

    @SneakyThrows
    @Test
    @AllureId("1398790")
    @DisplayName("C1398790.enabledContractSynchronization. Не передан x-tcs-login")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C1398790() {
        //вызываем метод enableContractSynchronization
        Response enableContractSynch = contractApiAdminCreator.get().enableContractSynchronization()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        ru.qa.tinkoff.swagger.tracking.model.ErrorResponse errorResponse = enableContractSynch.as(ErrorResponse.class);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        steps.checkHeaders(enableContractSynch, "x-trace-id", "x-server-time");
        //Проверяем тело ответа
        steps.checkErrors(errorResponse, "0344-00-Z99", "Сервис временно недоступен");
    }

    @SneakyThrows
    @Test
    @AllureId("1407957")
    @DisplayName("C1407957.enabledContractSynchronization. Не передан x-app-name")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C1407957() {
        //вызываем метод enableContractSynchronization
        Response enableContractSynch = contractApiAdminCreator.get().enableContractSynchronization()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xTcsLoginHeader("tracking")
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        ru.qa.tinkoff.swagger.tracking.model.ErrorResponse errorResponse = enableContractSynch.as(ErrorResponse.class);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        steps.checkHeaders(enableContractSynch, "x-trace-id", "x-server-time");
        //Проверяем тело ответа
        steps.checkErrors(errorResponse, "0344-00-Z99", "Сервис временно недоступен");
    }


    @SneakyThrows
    @Test
    @AllureId("1398809")
    @DisplayName("C1398809.enabledContractSynchronization. Slave status = untracked")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C1398809() {
        //вызываем метод enableContractSynchronization
        Response enableContractSynch = contractApiAdminCreator.get().enableContractSynchronization()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking")
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        ru.qa.tinkoff.swagger.tracking.model.ErrorResponse errorResponse = enableContractSynch.as(ErrorResponse.class);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        steps.checkHeaders(enableContractSynch, "x-trace-id", "x-server-time");
        //Проверяем тело ответа
        steps.checkErrors(errorResponse, "0344-14-B12", "Сервис временно недоступен");
    }

    @SneakyThrows
    @Test
    @AllureId("1398806")
    @DisplayName("C1398806.enabledContractSynchronization. Contract_id не существует")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C1398806() {
        //вызываем метод enableContractSynchronization
        Response enableContractSynch = contractApiAdminCreator.get().enableContractSynchronization()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking")
            .contractIdPath(notContractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(response -> response);
        ru.qa.tinkoff.swagger.tracking.model.ErrorResponse errorResponse = enableContractSynch.as(ErrorResponse.class);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        steps.checkHeaders(enableContractSynch, "x-trace-id", "x-server-time");
        //Проверяем тело ответа
        steps.checkErrors(errorResponse, "0344-14-B12", "Сервис временно недоступен");
    }

    @SneakyThrows
    @Test
    @AllureId("1398807")
    @DisplayName("C1398807.enabledContractSynchronization. Contract_id некорректной длины")
    @Subfeature("Альтернативные сценарии")
    @Description("Алгоритм предназначен для выставления заявки по выбранной для синхронизации позиции через вызов Middle.")
    void C1398807() {
        //вызываем метод enableContractSynchronization
        Response enableContractSynch = contractApiAdminCreator.get().enableContractSynchronization()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking")
            .contractIdPath(longContractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response);
        ru.qa.tinkoff.swagger.tracking.model.ErrorResponse errorResponse = enableContractSynch.as(ErrorResponse.class);
        //Проверяем, что в response есть заголовки x-trace-id и x-server-time
        steps.checkHeaders(enableContractSynch, "x-trace-id", "x-server-time");
        //Проверяем тело ответа
        steps.checkErrors(errorResponse, "0344-00-Z99", "Сервис временно недоступен");
    }

}
