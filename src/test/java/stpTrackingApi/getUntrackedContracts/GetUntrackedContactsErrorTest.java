package stpTrackingApi.getUntrackedContracts;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.swagger.tracking.api.ContractApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking.model.GetUntrackedContractsResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.configuration.client.TrackingApiClientAutoConfiguration;

import java.util.stream.Stream;


@Epic("getUntrackedContracts - Определение списка доступных для стратегии счетов")
@Feature("TAP-6652")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    TrackingApiClientAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class
})


public class GetUntrackedContactsErrorTest {
    @Autowired
    BillingService billingService;

    ContractApi contractApi = ApiClient.api(ApiClient.Config.apiConfig()).contract();

    @Test
    @AllureId("173560")
    @DisplayName("C173560.GetUntrackedContracts.SiebelId не передан в заголовке с key = 'X-TCS-SIEBEL-ID")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод возвращает список доступных договоров для подключения стратегии")
    void C173560() {
       //выполняем запрос без key = 'X-TCS-SIEBEL-ID", проверяем, что возвращается в ответ 401 код
        contractApi.getUntrackedContracts()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xDeviceIdHeader("new")
            .respSpec(spec -> spec.expectStatusCode(401))
            .execute(response -> response.as(GetUntrackedContractsResponse.class));
    }


    @Test
    @AllureId("445666")
    @DisplayName("C445666.GetUntrackedContracts.SiebelId = null в заголовке с key = 'X-TCS-SIEBEL-ID")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает список доступных договоров для подключения стратегии")
    void C445666() {
        //передаем в key = 'X-TCS-SIEBEL-ID" пустую строку, проверяем, что возвращается ответ 400 код
        contractApi.getUntrackedContracts()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader("")
            .xDeviceIdHeader("new")
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response.as(GetUntrackedContractsResponse.class));

    }


    @Test
    @AllureId("173582")
    @DisplayName("173582.GetUntrackedContracts.SiebelId несуществующие значение в заголовке с key = 'X-TCS-SIEBEL-ID")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает список доступных договоров для подключения стратегии")
    void C173582() {
        //передаем в key = 'X-TCS-SIEBEL-ID" несуществующие значение, проверяем, что возвращается ответ 500 код
        contractApi.getUntrackedContracts()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader("5-WDIFZIOO")
            .xDeviceIdHeader("new")
            .respSpec(spec -> spec.expectStatusCode(500))
            .execute(response -> response.as(GetUntrackedContractsResponse.class));
    }



    @Test
    @AllureId("455490")
    @DisplayName("455490.GetUntrackedContracts.SiebleId передан более 12 символов")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает список доступных договоров для подключения стратегии")
    void C455490() {
        ////передаем в key = 'X-TCS-SIEBEL-ID"  значение более 12 символов, проверяем, что возвращается ответ 400 код
        contractApi.getUntrackedContracts()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader("5-WDIFZIOOEROOR")
            .xDeviceIdHeader("new")
            .respSpec(spec -> spec.expectStatusCode(400))
            .execute(response -> response.as(GetUntrackedContractsResponse.class));
    }




    private static Stream<Arguments> provideStringsForHeadersCreateStrategy() {
        return Stream.of(
            Arguments.of(null, "android", "4.5.6"),
            Arguments.of("trading-invest", null, "I.3.7"),
            Arguments.of("trading", "ios 8.1", null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideStringsForHeadersCreateStrategy")
    @AllureId("447175")
    @DisplayName("447175.GetUntrackedContracts.Валидация обязательных параметров: X-APP-NAME, X-APP-VERSION, X-APP-PLATFORM")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает список доступных договоров для подключения стратегии")
    void C447175(String name, String version, String platform) {
        String SIEBEL_ID = "1-AHPZFN6";
        ContractApi.GetUntrackedContractsOper untrackedContracts = contractApi.getUntrackedContracts()
            .xTcsSiebelIdHeader(SIEBEL_ID)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            untrackedContracts = untrackedContracts.xAppNameHeader(name);
        }
        if (version != null) {
            untrackedContracts = untrackedContracts.xAppVersionHeader(version);
        }
        if (platform != null) {
            untrackedContracts = untrackedContracts.xPlatformHeader(platform);
        }
        untrackedContracts.execute(r -> r.as(GetUntrackedContractsResponse.class));
   }
}
