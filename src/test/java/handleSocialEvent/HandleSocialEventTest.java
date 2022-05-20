package handleSocialEvent;


import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaOldConfiguration;
import ru.qa.tinkoff.kafka.services.ByteToByteSenderService;
import ru.qa.tinkoff.kafka.oldkafkaservice.OldKafkaService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.enums.ClientStatusType;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.utils.UtilsTest;
import ru.tinkoff.trading.social.event.Social;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static ru.qa.tinkoff.kafka.Topics.SOCIAL_EVENT;


@Epic("handleSocialEvent - Обработка событий об изменении социального профиля в Пульсе")
@Feature("TAP-7386")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("SocialEvent")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tags({@Tag("handleSocialEventTest")})
@SpringBootTest(classes = {
//    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    KafkaOldConfiguration.class
})
public class HandleSocialEventTest {

    UtilsTest utilsTest = new UtilsTest();
    Client client;
    Profile profile;
    @Autowired
    ByteToByteSenderService kafkaSender;
    @Autowired
    OldKafkaService oldKafkaService;
    @Autowired
    ProfileService profileService;
    @Autowired
    ClientService clientService;
    @Autowired
    StpSiebel siebel;


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            if (client != null) {
                clientService.deleteClient(client);
            }
        });
    }



    @SneakyThrows
    @Test
    @AllureId("503903")
    @DisplayName("C503903.HandleSocialEvent.Успешное изменение social_profile: nickname")
    @Subfeature("Успешные сценарии")
    @Description("Операция для актуализации информации о социальном профиле ведущего в Пульсе " +
        "(объект client.social_profile) посредством обработки событий от системы Social.")
    void C503903() {
        //создаем запись в tracking.client, из БД Social заполняем инфо о профайле
        UUID investId = createClient(siebel.siebelSocial, ClientStatusType.confirmed);
        //получаем данные по профайлу
        profile = profileService.getProfileBySiebelId(siebel.siebelSocial);
        UUID key = profile.getId();
        UUID image = profile.getImage();
        String nickName = profile.getNickname();
        //формируем событие для топика kafka social.event
        OffsetDateTime now = OffsetDateTime.now();
        UUID eventId = UUID.fromString("ae516eb3-b5db-49f3-b966-862f5ade9bcb");
        Social.Event event = Social.Event.newBuilder()
            .setId(utilsTest.buildByteString(eventId))
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(now.getSecond())
                .setNanos(now.getNano())
                .build())
            .setAction(Social.Event.Action.UPDATED)
            .setProfile(Social.Profile.newBuilder()
                .setId(utilsTest.buildByteString(key))
                .setSiebelId(siebel.siebelSocial)
                .setNickname(nickName + "12345")
                .setImage(utilsTest.buildByteString(image))
                .build())
            .build();
        //кодируем событие по protobuf схеме social и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        byte[] keyBytes = event.getProfile().getId().toByteArray();
        //отправляем событие в топик kafka social.event
        oldKafkaService.send(SOCIAL_EVENT, keyBytes, eventBytes);
//        kafkaSender.send(SOCIAL_EVENT, keyBytes, eventBytes);
        //находим запись по клиенту и проверяем, что nickName изменился
        getClientByNickName(investId, nickName + "12345");
        client = clientService.getClient(investId);
        assertThat("номера договоров не равно", client.getId(), is(investId));
        assertThat("номера клиента не равно", client.getMasterStatus().toString(), is("confirmed"));
        assertThat("идентификатор профайла клиента не равно", client.getSocialProfile().getId(), is(profile.getId().toString()));
        assertThat("nickname клиента не равно", client.getSocialProfile().getNickname(), is(nickName + "12345"));
        assertThat("image клиента не равно", client.getSocialProfile().getImage(), is(image.toString()));
    }


    @SneakyThrows
    @Test
    @AllureId("503904")
    @DisplayName("C503904.HandleSocialEvent.Успешное изменение social_profile: image")
    @Subfeature("Успешные сценарии")
    @Description("Операция для актуализации информации о социальном профиле ведущего в Пульсе " +
        "(объект client.social_profile) посредством обработки событий от системы Social.")
    void C503904() {
        //создаем запись в tracking.client, из БД Social заполняем инфо о профайле
        UUID investId = createClient(siebel.siebelSocial, ClientStatusType.registered);
        //получаем данные по профайлу
        profile = profileService.getProfileBySiebelId(siebel.siebelSocial);
        UUID key = profile.getId();
        String nickName = profile.getNickname();
        //формируем событие для топика kafka social.event
        OffsetDateTime now = OffsetDateTime.now();
        UUID eventId = UUID.fromString("ae516eb3-b5db-49f3-b966-862f5ade9bcb");
        Social.Event event = Social.Event.newBuilder()
            .setId(utilsTest.buildByteString(eventId))
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(now.getSecond())
                .setNanos(now.getNano())
                .build())
            .setAction(Social.Event.Action.UPDATED)
            .setProfile(Social.Profile.newBuilder()
                .setId(utilsTest.buildByteString(key))
                .setSiebelId(siebel.siebelSocial)
                .setNickname(nickName)
                .setImage(utilsTest.buildByteString(UUID.fromString("f651139b-6879-463a-94b1-2f45e79b701d")))
                .build())
            .build();
        //кодируем событие по protobuff схеме social и переводим в byteArray

        //кодируем событие по protobuf схеме social и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        byte[] keyBytes = event.getProfile().getId().toByteArray();
        //отправляем событие в топик kafka social.event
//        kafkaSender.send(SOCIAL_EVENT, keyBytes, eventBytes);
        oldKafkaService.send(SOCIAL_EVENT, keyBytes, eventBytes);
        //находим запись по клиенту и проверяем, что image изменился
        getClientByImage(investId, "f651139b-6879-463a-94b1-2f45e79b701d");
        client = clientService.getClient(investId);
        assertThat("номера договоров не равно", client.getId(), is(investId));
        assertThat("номера клиента не равно", client.getMasterStatus().toString(), is("registered"));
        assertThat("идентификатор профайла клиента не равно", client.getSocialProfile().getId(), is(profile.getId().toString()));
        assertThat("nickname клиента не равно", client.getSocialProfile().getNickname(), is(nickName));
        assertThat("image клиента не равно", client.getSocialProfile().getImage(), is("f651139b-6879-463a-94b1-2f45e79b701d"));
    }


    @SneakyThrows
    @Test
    @AllureId("503909")
    @DisplayName("C503909.HandleSocialEvent.Успешное изменение social_profile: image = null")
    @Subfeature("Успешные сценарии")
    @Description("Операция для актуализации информации о социальном профиле ведущего в Пульсе " +
        "(объект client.social_profile) посредством обработки событий от системы Social.")
    void C503909() {
        //создаем запись в tracking.client, из БД Social заполняем инфо о профайле
        UUID investId = createClient(siebel.siebelSocial, ClientStatusType.registered);
        //получаем данные по профайлу
        profile = profileService.getProfileBySiebelId(siebel.siebelSocial);
        UUID key = profile.getId();
        String nickName = profile.getNickname();
        //формируем событие для топика kafka social.event
        OffsetDateTime now = OffsetDateTime.now();
        UUID eventId = UUID.fromString("ae516eb3-b5db-49f3-b966-862f5ade9bcb");
        Social.Event event = Social.Event.newBuilder()
            .setId(utilsTest.buildByteString(eventId))
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(now.getSecond())
                .setNanos(now.getNano())
                .build())
            .setAction(Social.Event.Action.UPDATED)
            .setProfile(Social.Profile.newBuilder()
                .setId(utilsTest.buildByteString(key))
                .setSiebelId(siebel.siebelSocial)
                .setNickname(nickName + "12345")
                .build())
            .build();
        //кодируем событие по protobuf схеме social и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        byte[] keyBytes = event.getProfile().getId().toByteArray();
        //отправляем событие в топик kafka social.event
//        kafkaSender.send(SOCIAL_EVENT, keyBytes, eventBytes);
        oldKafkaService.send(SOCIAL_EVENT, keyBytes, eventBytes);
        //находим запись по клиенту и проверяем, что nickName изменился
        getClientByNickName(investId, nickName + "12345");
        client = clientService.getClient(investId);
        assertThat("номера договоров не равно", client.getId(), is(investId));
        assertThat("номера клиента не равно", client.getMasterStatus().toString(), is("registered"));
        assertThat("идентификатор профайла клиента не равно", client.getSocialProfile().getId(), is(profile.getId().toString()));
        assertThat("nickname клиента не равно", client.getSocialProfile().getNickname(), is(nickName + "12345"));
        assertThat("image клиента не равно", client.getSocialProfile().getImage(), is(is(IsNull.nullValue())));
    }


    @SneakyThrows
    @Test
    @AllureId("507663")
    @DisplayName("C507663.HandleSocialEvent.Успешное изменение social_profile: nickname = null")
    @Subfeature("Успешные сценарии")
    @Description("Операция для актуализации информации о социальном профиле ведущего в Пульсе " +
        "(объект client.social_profile) посредством обработки событий от системы Social.")
    void C507663() {
        //создаем запись в tracking.client, из БД Social заполняем инфо о профайле
        UUID investId = createClient(siebel.siebelSocial, ClientStatusType.registered);
        //получаем данные по профайлу
        profile = profileService.getProfileBySiebelId(siebel.siebelSocial);
        UUID key = profile.getId();
        //формируем событие для топика kafka social.event
        OffsetDateTime now = OffsetDateTime.now();
        UUID eventId = UUID.fromString("ae516eb3-b5db-49f3-b966-862f5ade9bcb");
        Social.Event event = Social.Event.newBuilder()
            .setId(utilsTest.buildByteString(eventId))
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(now.getSecond())
                .setNanos(now.getNano())
                .build())
            .setAction(Social.Event.Action.UPDATED)
            .setProfile(Social.Profile.newBuilder()
                .setId(utilsTest.buildByteString(key))
                .setSiebelId(siebel.siebelSocial)
                .build())
            .build();
        //кодируем событие по protobuf схеме social и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        byte[] keyBytes = event.getProfile().getId().toByteArray();
        //отправляем событие в топик kafka social.event
//        kafkaSender.send(SOCIAL_EVENT, keyBytes, eventBytes);
        oldKafkaService.send(SOCIAL_EVENT, keyBytes, eventBytes);
        //находим запись по клиенту и проверяем, что nickName изменился
        getClientByNickName(investId, "");
        client = clientService.getClient(investId);
        assertThat("номера договоров не равно", client.getId(), is(investId));
        assertThat("номера клиента не равно", client.getMasterStatus().toString(), is("registered"));
        assertThat("идентификатор профайла клиента не равно", client.getSocialProfile().getId(), is(profile.getId().toString()));
        assertThat("nickname клиента не равно", client.getSocialProfile().getNickname(), is(""));
        assertThat("image клиента не равно", client.getSocialProfile().getImage(), is(IsNull.nullValue()));
    }


    @SneakyThrows
    @Test
    @AllureId("503902")
    @DisplayName("C503902.HandleSocialEvent.Profile_id в событии не существующие значение")
    @Subfeature("Успешные сценарии")
    @Description("Операция для актуализации информации о социальном профиле ведущего в Пульсе " +
        "(объект client.social_profile) посредством обработки событий от системы Social.")
    void C503902() {
        //создаем запись в tracking.client, из БД Social заполняем инфо о профайле
        UUID investId = createClient(siebel.siebelSocial, ClientStatusType.confirmed);
//        UUID investId = UUID.fromString("f5b3a54b-0ea3-44f4-af13-50e33d92646b");
        //получаем данные по профайлу
        profile = profileService.getProfileBySiebelId(siebel.siebelSocial);
        UUID key = profile.getId();
        UUID image = profile.getImage();
        String nickName = profile.getNickname();
        //формируем событие для топика kafka social.event
        UUID profileId = UUID.fromString("88888888-fd47-44e8-9c3b-3f468482cae0");
        OffsetDateTime now = OffsetDateTime.now();
        UUID eventId = UUID.fromString("ae516eb3-b5db-49f3-b966-862f5ade9bcb");
        Social.Event event = Social.Event.newBuilder()
            .setId(utilsTest.buildByteString(eventId))
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(now.getSecond())
                .setNanos(now.getNano())
                .build())
            .setAction(Social.Event.Action.UPDATED)
            .setProfile(Social.Profile.newBuilder()
                .setId(utilsTest.buildByteString(profileId))
                .setSiebelId(siebel.siebelSocial)
                .setNickname(nickName + "12345")
                .setImage(utilsTest.buildByteString(UUID.fromString("f651139b-6879-463a-94b1-2f45e79b701d")))
                .build())
            .build();
        //кодируем событие по protobuf схеме social и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        byte[] keyBytes = event.getProfile().getId().toByteArray();
        //отправляем событие в топик kafka social.event
//        //отправляем событие в топик kafka social.event
//        kafkaSender.send(SOCIAL_EVENT, keyBytes, eventBytes);
        oldKafkaService.send(SOCIAL_EVENT, keyBytes, eventBytes);
        //находим запись по клиенту и проверяем, что nickName  не изменился
        getClientByNickName(investId, nickName + "12345");
        client = clientService.getClient(investId);
        assertThat("номера договоров не равно", client.getId(), is(investId));
        assertThat("номера клиента не равно", client.getMasterStatus().toString(), is("confirmed"));
        assertThat("идентификатор профайла клиента не равно", client.getSocialProfile().getId(), is(profile.getId().toString()));
        assertThat("nickname клиента не равно", client.getSocialProfile().getNickname(), is(nickName));
        assertThat("image клиента не равно", client.getSocialProfile().getImage(), is(image.toString()));
    }


    @SneakyThrows
    @Test
    @AllureId("503906")
    @DisplayName("C503906.HandleSocialEvent.Social.event, action != 'UPDATED'")
    @Subfeature("Успешные сценарии")
    @Description("Операция для актуализации информации о социальном профиле ведущего в Пульсе " +
        "(объект client.social_profile) посредством обработки событий от системы Social.")
    void C503906() {
        //создаем запись в tracking.client, из БД Social заполняем инфо о профайле
        UUID investId = createClient(siebel.siebelSocial, ClientStatusType.confirmed);
        //получаем данные по профайлу
        profile = profileService.getProfileBySiebelId(siebel.siebelSocial);
        UUID key = profile.getId();
        UUID image = profile.getImage();
        String nickName = profile.getNickname();
        //формируем событие для топика kafka social.event
        OffsetDateTime now = OffsetDateTime.now();
        UUID eventId = UUID.fromString("ae516eb3-b5db-49f3-b966-862f5ade9bcb");
        Social.Event event = Social.Event.newBuilder()
            .setId(utilsTest.buildByteString(eventId))
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(now.getSecond())
                .setNanos(now.getNano())
                .build())
            .setAction(Social.Event.Action.DELETED)
            .setProfile(Social.Profile.newBuilder()
                .setId(utilsTest.buildByteString(key))
                .setSiebelId(siebel.siebelSocial)
                .setNickname(nickName + "12345")
                .setImage(utilsTest.buildByteString(UUID.fromString("f651139b-6879-463a-94b1-2f45e79b701d")))
                .build())
            .build();
        //кодируем событие по protobuf схеме social и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        byte[] keyBytes = event.getProfile().getId().toByteArray();
        //отправляем событие в топик kafka social.event
//        kafkaSender.send(SOCIAL_EVENT, keyBytes, eventBytes);
        oldKafkaService.send(SOCIAL_EVENT, keyBytes, eventBytes);
        //находим запись по клиенту и проверяем, что nickName изменился
        getClientByNickName(investId, nickName + "12345");
        client = clientService.getClient(investId);
        assertThat("номера договоров не равно", client.getId(), is(investId));
        assertThat("номера клиента не равно", client.getMasterStatus().toString(), is("confirmed"));
        assertThat("идентификатор профайла клиента не равно", client.getSocialProfile().getId(), is(profile.getId().toString()));
        assertThat("nickname клиента не равно", client.getSocialProfile().getNickname(), is(nickName));
        assertThat("image клиента не равно", client.getSocialProfile().getImage(), is(image.toString()));
    }


    @SneakyThrows
    @Test
    @AllureId("503908")
    @DisplayName("C503908.HandleSocialEvent.Запись в таблице tracking.client master_status not in ('confirmed', 'registered')")
    @Subfeature("Успешные сценарии")
    @Description("Операция для актуализации информации о социальном профиле ведущего в Пульсе " +
        "(объект client.social_profile) посредством обработки событий от системы Social.")
    void C503908() {
        //создаем запись в tracking.client, из БД Social заполняем инфо о профайле
        UUID investId = createClient(siebel.siebelSocial, ClientStatusType.none);
        //получаем данные по профайлу
        profile = profileService.getProfileBySiebelId(siebel.siebelSocial);
        UUID key = profile.getId();
        UUID image = profile.getImage();
        String nickName = profile.getNickname();
        //формируем событие для топика kafka social.event
        OffsetDateTime now = OffsetDateTime.now();
        UUID eventId = UUID.fromString("ae516eb3-b5db-49f3-b966-862f5ade9bcb");
        Social.Event event = Social.Event.newBuilder()
            .setId(utilsTest.buildByteString(eventId))
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(now.getSecond())
                .setNanos(now.getNano())
                .build())
            .setAction(Social.Event.Action.UPDATED)
            .setProfile(Social.Profile.newBuilder()
                .setId(utilsTest.buildByteString(key))
                .setSiebelId(siebel.siebelSocial)
                .setNickname(nickName + "12345")
                .setImage(utilsTest.buildByteString(image))
                .build())
            .build();
        //кодируем событие по protobuf схеме social и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        byte[] keyBytes = event.getProfile().getId().toByteArray();
        //отправляем событие в топик kafka social.event
//        kafkaSender.send(SOCIAL_EVENT, keyBytes, eventBytes);
        oldKafkaService.send(SOCIAL_EVENT, keyBytes, eventBytes);
        //находим запись по клиенту и проверяем, что nickName изменился
        getClientByNickName(investId, nickName + "12345");
        client = clientService.getClient(investId);
        assertThat("номера договоров не равно", client.getId(), is(investId));
        assertThat("номера клиента не равно", client.getMasterStatus().toString(), is("none"));
        assertThat("идентификатор профайла клиента не равно", client.getSocialProfile().getId(), is(profile.getId().toString()));
        assertThat("nickname клиента не равно", client.getSocialProfile().getNickname(), is(nickName));
        assertThat("image клиента не равно", client.getSocialProfile().getImage(), is(image.toString()));
    }


//методы для работы тестов*****************************************************************************

    //метод находит подходящий siebleId в сервисе счетов и создаем запись по нему в табл. tracking.client
    public UUID createClient(String SIEBLE_ID, ClientStatusType сlientStatusType) {
        UUID investId = UUID.fromString("79c45a66-bab7-4599-a9c9-2f67d476dd79");
        //находим данные по клиенту в БД social
        profile = profileService.getProfileBySiebelId(SIEBLE_ID);
        client = clientService.createClient(investId, сlientStatusType, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString()), null);
        return investId;
    }


    //проверяем запись в tracking.client по nickName после отправки события
    public void getClientByNickName(UUID investId, String nickName) throws InterruptedException {
        await().atMost(TEN_SECONDS).until(() ->
            clientService.getClientByNickname(investId, nickName), notNullValue());
    }

    //проверяем запись в tracking.client по image после отправки события
    public void getClientByImage(UUID investId, String image) throws InterruptedException {
        await().atMost(TEN_SECONDS).until(() ->
            clientService.getClientByImage(investId, image), notNullValue());
    }
}
