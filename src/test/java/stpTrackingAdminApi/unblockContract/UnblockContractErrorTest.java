package stpTrackingAdminApi.unblockContract;

import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrderDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.ContractApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.swagger.tracking_admin.model.ErrorResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.tracking.services.grpc.MiddleGrpcService;
import ru.qa.tinkoff.tracking.services.grpc.utils.GrpcServicesAutoConfiguration;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("unblockContract Разблокировка договора")
@Feature("TAP-12708")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@Subfeature("Альтернативные сценарии")
@Owner("ext.ebelyaninov")
@Tags({@Tag("stp-tracking-admin"), @Tag("unblockContract")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    GrpcServicesAutoConfiguration.class
    //ClientApiAdminCreator.class
})
public class UnblockContractErrorTest {
    @Autowired
    MiddleGrpcService middleGrpcService;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    SlavePortfolioDao slavePortfolioDao;
    @Autowired
    SlaveOrderDao slaveOrderDao;
    @Autowired
    StrategyService strategyService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    StpTrackingSlaveSteps steps;
//    @Autowired
//    ApiAdminCreator<ContractApi> apiApiAdminCreator;

    ContractApi contractApi = ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.api(ApiClient.Config.apiConfig()).contract();

    String xApiKey = "x-api-key";
    String key= "tracking";
    String keyRead = "tcrm";
    SlavePortfolio slavePortfolio;

    ru.qa.tinkoff.tracking.entities.Client clientSlave;
    String contractIdMaster;
    String contractIdSlave;
    Contract contract;
    UUID strategyId;
    String SIEBEL_ID_MASTER = "5-4LCY1YEB";
    String SIEBEL_ID_SLAVE = "5-JEF71TBN";
    UUID investIdMaster;
    UUID investIdSlave;


    @BeforeAll
    void getDataFromAccount(){
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID_MASTER);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(SIEBEL_ID_SLAVE);
        investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
    }

    @BeforeEach
    @SneakyThrows
    void createClient() {
        strategyId = UUID.randomUUID();
        String description = "new test стратегия autotest";
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(subscriptionService.getSubscriptionByContract(contractIdSlave));
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractService.getContract(contractIdSlave));
            } catch (Exception e) {
            }
            try {
                clientSlave = clientService.getClient(investIdSlave);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(steps.clientSlave);
            } catch (Exception e) {
            }
            try {
                trackingService.deleteStrategy(steps.strategy);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(steps.contractMaster);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(steps.clientMaster);
            } catch (Exception e) {
            }
            try {
                steps.createEventInTrackingEvent(contractIdSlave);
            } catch (Exception e) {
            }
        });
    }

    private static Stream<Arguments> provideStringsForNotFoundContract () {
        return Stream.of(
            Arguments.of(ContractState.untracked, true),
            Arguments.of(ContractState.tracked, false),
            Arguments.of(null, true)
        );
    }


    @ParameterizedTest
    @MethodSource("provideStringsForNotFoundContract")
    @AllureId("1491052")
    @DisplayName("C1491052.unblockContract - Не нашли запись в contract")
    @Description("Разблокировка договора")
    void C1491052(ContractState contractState, Boolean blocked) {

        if (contractState == null){
            try {
                contractService.deleteContract(contractService.getContract(contractIdSlave));
            } catch (Exception e) {
            }
        }
        else {
            //создаем запись о клиенте в tracking.client
            clientSlave = clientService.createClient(investIdSlave, ClientStatusType.none, null, ClientRiskProfile.aggressive);
            // создаем запись о договоре клиента в tracking.contract
            contract = new Contract()
                .setId(contractIdSlave)
                .setClientId(clientSlave.getId())
//                .setRole(null)
                .setState(contractState)
                .setBlocked(blocked);

            if (contractState.equals(ContractState.tracked)){
                contract.setStrategyId(strategyId);
            }
            contract = contractService.saveContract(contract);
        }
            //Вызываем метод на разблокировку контракта
            ErrorResponse unblockContract = contractApi.unblockContract()
                .contractIdPath(contractIdSlave)
                .xAppNameHeader("tracking")
                .xTcsLoginHeader("login")
                .reqSpec(r -> r.addHeader(xApiKey, key))
                .respSpec(spec -> spec.expectStatusCode(500))
                .execute(response -> response.as(ErrorResponse.class));

        checkErrorFromResponce(unblockContract, "0344-15-B14", "Не найден заблокированный договор");
    }

    @Test
    @AllureId("1705765")
    @DisplayName("C1705765.unblockContract - Заголовок X-API-KEY с доступом read")
    @Description(" Разблокировка договора")
    void C1705765() {
         contractApi.unblockContract()
            .contractIdPath(contractIdSlave)
            .xAppNameHeader("tracking")
            .xTcsLoginHeader("login")
             .reqSpec(r -> r.addHeader(xApiKey, keyRead))
            .respSpec(spec -> spec.expectStatusCode(401));
    }

    @Test
    @AllureId("1491051")
    @DisplayName("C1491051.unblockContract - Заголовок X-API-KEY не передан")
    @Description(" Разблокировка договора")
    void C1491051() {
        contractApi.unblockContract()
            .contractIdPath(contractIdSlave)
            .xAppNameHeader("tracking")
            .xTcsLoginHeader("login")
            .respSpec(spec -> spec.expectStatusCode(401));
    }

    private static Stream<Arguments> provideStringsForValidation () {
        return Stream.of(
            Arguments.of(null, "login", null),
            Arguments.of("tracking", null, null),
            Arguments.of("tracking", "login", "test")
        );
    }


    @ParameterizedTest
    @MethodSource("provideStringsForValidation")
    @AllureId("1491050")
    @DisplayName("C1491050.unblockContract - Не нашли запись в contract")
    @Description("Разблокировка договора")
    void C1491050(String xAppName, String xTcsLogin, String xB3Sampled) {

        ContractApi.UnblockContractOper unblockContract = contractApi.unblockContract()
            .contractIdPath(contractIdSlave)
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .respSpec(spec -> spec.expectStatusCode(400));
        if (xAppName != null){
            unblockContract = unblockContract.xAppNameHeader(xAppName);
        }
        if (xTcsLogin != null){
            unblockContract = unblockContract.xTcsLoginHeader(xTcsLogin);
        }
        if (xB3Sampled != null){
            unblockContract = unblockContract.xB3SampledHeader(xB3Sampled);
        }
        ErrorResponse getErrorResponce = unblockContract
            .execute(response -> response.as(ErrorResponse.class));
        checkErrorFromResponce(getErrorResponce, "0344-00-Z99", "Сервис временно недоступен");
    }

    void checkErrorFromResponce (ErrorResponse getSignalsResponse, String errorCode, String errorMessage){
        assertThat("код ошибки не равно", getSignalsResponse.getErrorCode(), is(errorCode));
        assertThat("Сообщение об ошибке не равно", getSignalsResponse.getErrorMessage(), is(errorMessage));
    }
}
