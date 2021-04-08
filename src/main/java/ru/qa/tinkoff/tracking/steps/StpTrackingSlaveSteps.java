package ru.qa.tinkoff.tracking.steps;

import com.google.protobuf.Timestamp;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.kafka.kafkaClient.KafkaHelper;
import ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream.PriceUpdatedEvent;
import ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream.PriceUpdatedKey;
import ru.qa.tinkoff.swagger.MD.api.PricesApi;
import ru.qa.tinkoff.swagger.trackingSlaveCache.api.CacheApi;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.tinkoff.invest.sdet.kafka.protobuf.sender.KafkaProtobufCustomSender;
import ru.tinkoff.trading.tracking.Tracking;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StpTrackingSlaveSteps {

    private final ContractService contractService;
    private final TrackingService trackingService;
    private final ClientService clientService;
    //private final MasterPortfolioDao masterPortfolioDao;
//    KafkaHelper kafkaHelper = new KafkaHelper();
//
//    @Resource(name = "customSenderFactory")
//    KafkaProtobufCustomSender<String, byte[]> kafkaSender;


    PricesApi pricesApi = ru.qa.tinkoff.swagger.MD.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.MD.invoker.ApiClient.Config.apiConfig()).prices();
    CacheApi cacheApi = ru.qa.tinkoff.swagger.trackingSlaveCache.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.trackingSlaveCache.invoker.ApiClient.Config.apiConfig()).cache();

    //метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    void createClientWithContractAndStrategy(Client clientMaster, Contract contractMaster, Strategy strategy,
                                             UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
                                             UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                             ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                             StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
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
        strategy = new Strategy()
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

        strategy = trackingService.saveStrategy(strategy);
    }

}
