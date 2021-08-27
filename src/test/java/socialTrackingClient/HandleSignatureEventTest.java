package socialTrackingClient;

import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.*;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.invest.signature.event.SignatureEvent;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class

})

public class HandleSignatureEventTest {

    UtilsTest utilsTest = new UtilsTest();

    @Autowired
    ClientService clientService;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    TrackingService trackingService;
    @Autowired
    ContractService contractService;
    @Autowired
    ByteToByteSenderService kafkaSender;


    String SIEBEL_ID = "1-1LGJ72C";
    String contractId;
    Client client;
    UUID investId;
    int TRACKING_LEADING = 0;
    OffsetDateTime time = OffsetDateTime.now();

    @BeforeAll
    void getdataFromInvestmentAccount() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(SIEBEL_ID);
        investId = resAccountMaster.getInvestId();
        contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
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
    @AllureId("1205245")
    @DisplayName("C1205245. Подписании договора управляющего")
    @Subfeature("Успешные сценарии")
    @Description("Обработка событий о подписании договора управляющего")
    void C1205245() {
        //Создаем клиента в табл. client
        createClient(investId, ClientStatusType.confirmed, null);
        //Формируем и отправляем событие событие в топик origination.signature.notification.raw
        byte[] eventBytes = createMessageForHandleSignatureEvent(TRACKING_LEADING, investId, time).toByteArray();
        byte[] keyBytes = createMessageForHandleSignatureEvent(TRACKING_LEADING, investId, time).getId().toByteArray();
        kafkaSender.send(ORIGINATION_SIGNATURE_NOTIFICATION, keyBytes, eventBytes);
        await().atMost(Duration.ofSeconds(5))
            .until(() -> clientService.getClient(investId).getMasterStatus().equals(ClientStatusType.registered));
        Client getDataFromClient = clientService.getClient(investId);
        assertThat("master.status !=" + ClientStatusType.registered, getDataFromClient.getMasterStatus(), equalTo(ClientStatusType.registered));
    }

    //*** Методы для работы тестов ***
    //Метод для создания записи  в табл. tracking.client
    void createClient(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile) {
        client = clientService.createClient(investId, clientStatusType, socialProfile);
    }

    //Метод для создания события по схеме signature-event.proto
    SignatureEvent.Event createMessageForHandleSignatureEvent(int typeValue, UUID investId, OffsetDateTime time){

        return  SignatureEvent.Event.newBuilder()
            .setId(utilsTest.buildByteString(UUID.randomUUID()))
            .setTypeValue(typeValue)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .setAccount(SignatureEvent.Event.newBuilder()
                .getAccountBuilder()
                     .setId(utilsTest.buildByteString(investId))
                     .build())
            .build();
    }
}
