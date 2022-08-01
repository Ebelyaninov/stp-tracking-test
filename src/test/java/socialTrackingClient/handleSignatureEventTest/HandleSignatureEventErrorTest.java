package socialTrackingClient.handleSignatureEventTest;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaOldConfiguration;
import ru.qa.tinkoff.kafka.oldkafkaservice.OldKafkaService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.invest.signature.event.SignatureEvent;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static ru.qa.tinkoff.kafka.Topics.*;

@Slf4j
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("handleSignatureEvent Обработка событий о подписании договора управляющего")
@Feature("TAP-6569")
@DisplayName("social-tracking-client")
@Owner("ext.ebelyaninov")
@Tags({@Tag("social-tracking-client"),@Tag("handleSignatureEvent")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    KafkaOldConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class
})

public class HandleSignatureEventErrorTest {

    UtilsTest utilsTest = new UtilsTest();
    Client client;

    @Autowired
    ClientService clientService;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    ContractService contractService;
    @Autowired
    OldKafkaService oldKafkaService;
    @Autowired
    StpSiebel stpSiebel;

    String SIEBEL_ID;
    String contractId;

    UUID investId;
    int TRACKING_LEADING = 0;
    int TEST_TYPE = 1;
    OffsetDateTime time = OffsetDateTime.now();

    @BeforeAll
    void getdataFromInvestmentAccount() {
        SIEBEL_ID = stpSiebel.siebelIdMasterForClient;
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(SIEBEL_ID);
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                contractService.deleteContract(contractService.getContract(contractId));
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientService.getClient(investId));
            } catch (Exception e) {
            }
        });
    }


    @Test
    @AllureId("1205247")
    @DisplayName("C1205247. Не нашли запись для обновления")
    @Subfeature("Успешные сценарии")
    @Description("Обработка событий о подписании договора управляющего")
    void C1205247() {

        try {
            clientService.deleteClient(clientService.getClient(investId));
        } catch (Exception e) {
        }

        //Формируем и отправляем событие событие в топик origination.signature.notification.raw
        byte[] eventBytes = createMessageForHandleSignatureEvent(TRACKING_LEADING, investId, time).toByteArray();
        byte[] keyBytes = createMessageForHandleSignatureEvent(TRACKING_LEADING, investId, time).getId().toByteArray();
        oldKafkaService.send(ORIGINATION_SIGNATURE_NOTIFICATION, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(2));
        assertThat("найдена запись в client", clientService.getClientByIdAndMasterStatusAndReturnIfFound(investId, ClientStatusType.registered), is(nullValue()));
    }

    @Test
    @AllureId("1205250")
    @DisplayName("C1205250. Игнорируем событие с type != 'TRACKING_LEADING'")
    @Subfeature("Успешные сценарии")
    @Description("Обработка событий о подписании договора управляющего")
    void C1205250() {

        createClient(investId, ClientStatusType.confirmed, null);
        //Формируем и отправляем событие событие в топик origination.signature.notification.raw
        byte[] eventBytes = createMessageForHandleSignatureEvent(TEST_TYPE, investId, time).toByteArray();
        byte[] keyBytes = createMessageForHandleSignatureEvent(TEST_TYPE, investId, time).getId().toByteArray();
        oldKafkaService.send(ORIGINATION_SIGNATURE_NOTIFICATION, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(2));
        Client getDataFromClient = clientService.getClient(investId);
        assertThat("master_status != confirmed", getDataFromClient.getMasterStatus(), equalTo(ClientStatusType.confirmed));
    }

    private static Stream<Arguments> provideMasterStatusForClient () {
        return Stream.of(
            Arguments.of(ClientStatusType.none),
            Arguments.of(ClientStatusType.registered)
        );
    }

    @ParameterizedTest
    @MethodSource("provideMasterStatusForClient")
    @AllureId("1205248")
    @DisplayName("C1205248. Запись не подходит под условия")
    @Subfeature("Успешные сценарии")
    @Description("Обработка событий о подписании договора управляющего")
    void C1205248(ClientStatusType masterStatus) {

        createClient(investId, masterStatus, null);
        //Формируем и отправляем событие событие в топик origination.signature.notification.raw
        byte[] eventBytes = createMessageForHandleSignatureEvent(TEST_TYPE, investId, time).toByteArray();
        byte[] keyBytes = createMessageForHandleSignatureEvent(TEST_TYPE, investId, time).getId().toByteArray();
        oldKafkaService.send(ORIGINATION_SIGNATURE_NOTIFICATION, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(2));
        Client getDataFromClient = clientService.getClient(investId);
        assertThat("master_status != " + masterStatus, getDataFromClient.getMasterStatus(), equalTo(masterStatus));
    }



    //*** Методы для работы тестов ***
    //Метод для создания записи  в табл. tracking.client
    void createClient(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile) {
        client = clientService.createClient(investId, clientStatusType, socialProfile, null);
    }

    //Метод для создания события по схеме signature-event.proto
    SignatureEvent.Event createMessageForHandleSignatureEvent(int typeValue, UUID investId, OffsetDateTime time){

        ByteString bs = utilsTest.buildByteString(investId);

        return  SignatureEvent.Event.newBuilder()
            .setId(utilsTest.buildByteString(UUID.randomUUID()))
            .setTypeValue(typeValue)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setAccount(SignatureEvent.Account.newBuilder()
                     .setId(bs)
                     .build())
            .build();
    }
}
