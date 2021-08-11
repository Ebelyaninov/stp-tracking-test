package ru.qa.tinkoff.steps.trackingAdminSteps;


import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.api.ExchangePositionApi;
import ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.ExchangePositionService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

@Slf4j
@Service
@RequiredArgsConstructor
public class StpTrackingAdminSteps {
    private final ContractService contractService;
    private final TrackingService trackingService;
    private final ClientService clientService;
    private final ByteArrayReceiverService kafkaReceiver;
    private final ProfileService profileService;
    private final ExchangePositionService exchangePositionService;
    public Client client;
    public Contract contract;
    public Strategy strategy;

    ExchangePositionApi exchangePositionApi = ApiClient.api(ApiClient.Config.apiConfig()).exchangePosition();
    ru.qa.tinkoff.tracking.entities.ExchangePosition exchangePosition;

    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker
        .ApiClient.api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();

    public GetBrokerAccountsResponse getBrokerAccounts (String SIEBEL_ID) {
        GetBrokerAccountsResponse resAccount = brokerAccountApi.getBrokerAccountsBySiebel()
            .siebelIdPath(SIEBEL_ID)
            .brokerTypeQuery("broker")
            .brokerStatusQuery("opened")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetBrokerAccountsResponse.class));
        return resAccount;
    }

    //Метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    public void createClientWithContractAndStrategy(UUID investId, SocialProfile socialProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                                    UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                                    ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                                    StrategyStatus strategyStatus, int slaveCount, LocalDateTime date, Integer score) {
        //создаем запись о клиенте в tracking.client
        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);
        // создаем запись о договоре клиента в tracking.contract
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contract = contractService.saveContract(contract);
        //создаем запись о стратегии клиента
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("range", new BigDecimal("0.2"));
        feeRateProperties.put("management", new BigDecimal("0.04"));
        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contract)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(score)
            .setFeeRate(feeRateProperties);
        strategy = trackingService.saveStrategy(strategy);
    }

    @Step("Перемещение offset до текущей позиции")
    public void resetOffsetToLate(Topics topic) {
        log.info("Получен запрос на вычитывание всех сообщений из Kafka топика {} ", topic.getName());
        await().atMost(Duration.ofSeconds(30))
            .until(() -> kafkaReceiver.receiveBatch(topic, Duration.ofSeconds(3)), List::isEmpty);
        log.info("Все сообщения из {} топика вычитаны", topic.getName());
    }


    @Step("Данные по profile из social")
    public SocialProfile getProfile(String SIEBEL_ID) {
        Profile profile = profileService.getProfileBySiebelId(SIEBEL_ID);
        SocialProfile socialProfile = new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname());
//            .setImage(profile.getImage().toString());
        return socialProfile;
    }


    //Метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWithContractAndStrategy(Client client, Contract contract, Strategy strategy, UUID investId,
                                                    SocialProfile socialProfile, String contractId, ContractRole contractRole,
                                                    ContractState contractState, UUID strategyId, String title, String description,
                                                    StrategyCurrency strategyCurrency, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                                    StrategyStatus strategyStatus, int slaveCount, LocalDateTime date, Integer score) {

        client = clientService.createClient(investId, ClientStatusType.registered, socialProfile);
        contract = new Contract()
            .setId(contractId)
            .setClientId(client.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);

        contract = contractService.saveContract(contract);

        strategy = new Strategy()
            .setId(strategyId)
            .setContract(contract)
            .setTitle(title)
            .setBaseCurrency(strategyCurrency)
            .setRiskProfile(strategyRiskProfile)
            .setDescription(description)
            .setStatus(strategyStatus)
            .setSlavesCount(slaveCount)
            .setActivationTime(date)
            .setScore(score);
        strategy = trackingService.saveStrategy(strategy);
    }

    //создаем запись в tracking.exchange_position по инструменту
    public void createExchangePosition(String ticker, String tradingClearingAccount, ExchangePositionExchange exchangePositionExchange,
                                       String otcTicker, String otcClassCode) {
        Map<String, Integer> mapValue = new HashMap<String, Integer>();
        mapValue.put("default", 100);
        mapValue.put("primary", 100);
        exchangePosition = new ru.qa.tinkoff.tracking.entities.ExchangePosition()
            .setTicker(ticker)
            .setTradingClearingAccount(tradingClearingAccount)
            .setExchangePositionExchange(exchangePositionExchange)
            .setTrackingAllowed(false)
            .setDailyQuantityLimit(200)
            .setOrderQuantityLimits(mapValue)
            .setOtcTicker(otcTicker)
            .setOtcClassCode(otcClassCode);
        exchangePosition = exchangePositionService.saveExchangePosition(exchangePosition);
    }

}
