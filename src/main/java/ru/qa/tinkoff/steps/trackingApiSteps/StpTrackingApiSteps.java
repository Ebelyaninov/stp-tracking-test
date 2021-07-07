package ru.qa.tinkoff.steps.trackingApiSteps;

import com.google.protobuf.ByteString;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

@Slf4j
@Service
@RequiredArgsConstructor
public class StpTrackingApiSteps {

    @Autowired(required = false)
    ByteArrayReceiverService kafkaReceiver;
    @Autowired(required = false)
    ProfileService profileService;
    @Autowired(required = false)
    ClientService clientService;
    @Autowired(required = false)
    ContractService contractService;
    @Autowired(required = false)
    StrategyService strategyService;
    @Autowired(required = false)
    TrackingService trackingService;


    Profile profile;
    Client clientMaster;
    Contract contractMaster;
    Strategy strategyMaster;
    Client clientSlave;
    Contract contractSlave;


    //Метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWintContractAndStrategy(String SIEBLE_ID, UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                             ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
//        //находим данные по клиенту в БД social
//        profile = profileService.getProfileBySiebelId(SIEBLE_ID);
        //создаем запись о клиенте в tracking.client
        clientMaster = clientService.createClient(investId, ClientStatusType.registered, null);
        // создаем запись о договоре клиента в tracking.contract
        contractMaster = new Contract()
            .setId(contractId)
            .setClientId(clientMaster.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contractMaster = contractService.saveContract(contractMaster);
        //создаем запись о стратегии клиента
        strategyMaster = new Strategy()
            .setId(strategyId)
            .setContract(contractMaster)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(1);
        strategyMaster = trackingService.saveStrategy(strategyMaster);
    }



    //метод создает клиента
    public void createClient(String SIEBLE_ID, UUID investId, ClientStatusType clientStatusType) {
        //находим данные по клиенту в БД social
        profile = profileService.getProfileBySiebelId(SIEBLE_ID);
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, clientStatusType, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString()));
    }

    //метод создает клиента c договором
    public void createClientWithContract(String SIEBLE_ID, UUID investId, ClientStatusType clientStatusType,
                                  String contractId, ContractRole contractRole, ContractState contractState,
                                  UUID strategyId) {
        //находим данные по клиенту в БД social
        profile = profileService.getProfileBySiebelId(SIEBLE_ID);
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, clientStatusType, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString()));
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contractSlave = contractService.saveContract(contractSlave);
    }



    @Step("Переместить offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(3)), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());

    }


    public UUID uuid(ByteString bytes) {
        ByteBuffer buff = bytes.asReadOnlyByteBuffer();
        return new UUID(buff.getLong(), buff.getLong());
    }

}
