package ru.qa.tinkoff.steps.trackingApiSteps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.vladmihalcea.hibernate.type.range.Range;
import com.google.protobuf.Timestamp;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.MD.api.PricesApi;
import ru.qa.tinkoff.swagger.investAccountPublic.api.BrokerAccountApi;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.trackingCache.api.CacheApi;
import ru.qa.tinkoff.swagger.trackingCache.invoker.ApiClient;
import ru.qa.tinkoff.swagger.trackingCache.model.Entity;
import ru.qa.tinkoff.swagger.tracking_admin.api.ContractApi;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.*;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;
import ru.tinkoff.trading.tracking.Tracking;

import javax.swing.text.StyledEditorKit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.http.HttpStatus.SC_OK;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_CONTRACT_EVENT;
import static ru.qa.tinkoff.kafka.Topics.TRACKING_EVENT;
import static ru.qa.tinkoff.swagger.trackingCache.invoker.ResponseSpecBuilders.shouldBeCode;
import static ru.qa.tinkoff.swagger.trackingCache.invoker.ResponseSpecBuilders.validatedWith;

@Slf4j
@Service
@RequiredArgsConstructor
public class StpTrackingApiSteps {

    BrokerAccountApi brokerAccountApi = ru.qa.tinkoff.swagger.investAccountPublic.invoker
        .ApiClient.api(ru.qa.tinkoff.swagger.investAccountPublic.invoker.ApiClient.Config.apiConfig()).brokerAccount();
    PricesApi pricesApi = ru.qa.tinkoff.swagger.MD.invoker.ApiClient
        .api(ru.qa.tinkoff.swagger.MD.invoker.ApiClient.Config.apiConfig()).prices();
    SubscriptionApi subscriptionApi = ru.qa.tinkoff.swagger.tracking.invoker
        .ApiClient.api(ru.qa.tinkoff.swagger.tracking.invoker.ApiClient.Config.apiConfig()).subscription();
    ContractApi contractApi = ru.qa.tinkoff.swagger.tracking_admin.invoker
        .ApiClient.api(ru.qa.tinkoff.swagger.tracking_admin.invoker.ApiClient.Config.apiConfig()).contract();

    CacheApi cacheApi = ru.qa.tinkoff.swagger.trackingCache.invoker.ApiClient.api(ApiClient.Config.apiConfig()).cache();



    private final ByteArrayReceiverService kafkaReceiver;
    private final ContractService contractService;
    private final TrackingService trackingService;
    private final ClientService clientService;
    private final ProfileService profileService;
    private final SubscriptionService subscriptionService;
    private final SubscriptionBlockService subscriptionBlockService;
    @Autowired(required = false)
    MasterPortfolioDao masterPortfolioDao;
    public Client clientMaster;
    public Contract contractMaster;
    public Strategy strategyMaster;
    private final StringToByteSenderService kafkaSender;


    public Client clientSlave;
    public Contract contractSlave;
    public Subscription subscription;
    public SubscriptionBlock subscriptionBlock;
    Profile profile;
//    Client clientSlave;
//    Contract contractSlave;
//    Subscription subscription;



    public String ticker1 = "SBER";
    public String tradingClearingAccount1 = "NDS000000001";
    //    public String tradingClearingAccount1 = "L01+00000F00";
    public String quantity1 = "50";
    public String sector1 = "financial";
    public String type1 = "share";
    public String company1 = "Сбер Банк";
    public String classCode1 = "TQBR";
    public String instrumet1 = ticker1 + "_" + classCode1;

    public String ticker2 = "SU29009RMFS6";
    //   public String tradingClearingAccount2 = "L01+00000F00";
    public String tradingClearingAccount2 = "NDS000000001";
    public String quantity2 = "3";
    public String classCode2 = "TQOB";
    public String sector2 = "government";
    public String type2 = "bond";
    public String company2 = "ОФЗ";
    public String instrumet2 = ticker2 + "_" + classCode2;
    //
    public String ticker3 = "LKOH";
    public String tradingClearingAccount3 = "NDS000000001";
    //    public String tradingClearingAccount3 = "L01+00000F00";
    public String quantity3 = "7";
    public String classCode3 = "TQBR";
    public String sector3 = "energy";
    public String type3 = "share";
    public String company3 = "Лукойл";
    public String instrumet3 = ticker3 + "_" + classCode3;

    public String ticker4 = "SNGSP";
    public String tradingClearingAccount4 = "NDS000000001";
    //    public String tradingClearingAccount4 = "L01+00000F00";
    public String quantity4 = "100";
    public String classCode4 = "TQBR";
    public String sector4 = "energy";
    public String type4 = "share";
    public String company4 = "Сургутнефтегаз";
    public String instrumet4 = ticker4 + "_" + classCode4;

    public String ticker5 = "TRNFP";
    public String tradingClearingAccount5 = "NDS000000001";
    //    public String tradingClearingAccount5 = "L01+00000F00";
    public String quantity5 = "4";
    public String classCode5 = "TQBR";
    public String sector5 = "energy";
    public String type5 = "share";
    public String company5 = "Транснефть";
    public String instrumet5 = ticker5 + "_" + classCode5;


    public String ticker6 = "ESGR";
    public String tradingClearingAccount6 = "NDS000000001";
    //    public String tradingClearingAccount6 = "L01+00000F00";
    public String quantity6 = "5";
    public String classCode6 = "TQTF";
    public String sector6 = "other";
    public String type6 = "etf";
    public String company6 = "РСХБ Управление Активами";
    public String instrumet6 = ticker6 + "_" + classCode6;

    public String ticker7 = "USD000UTSTOM";
    public String tradingClearingAccount7 = "MB9885503216";
    public String quantity7 = "1000";
    public String classCode7 = "CETS";
    public String sector7 = "money";
    public String type7 = "money";
    public String company7 = "Денежные средства";
    public String instrumet7 = ticker7 + "_" + classCode7;


    public String ticker8 = "YNDX";
    //    public String tradingClearingAccount8 = "L01+00000F00";
    public String tradingClearingAccount8 = "NDS000000001";
    public String quantity8 = "3";
    public String classCode8 = "TQBR";
    public String sector8 = "telecom";
    public String type8 = "share";
    public String company8= "Яндекс";
    public String instrumet8 = ticker8 + "_" + classCode8;





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
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWintContractAndStrategy(String SIEBLE_ID, UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                                    UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                                    ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                                    StrategyStatus strategyStatus, int slaveCount, LocalDateTime date, Boolean overloaded) {
        //находим данные по клиенту в БД social
        String image = "";
        profile = profileService.getProfileBySiebelId(SIEBLE_ID);
        if (profile.getImage() == null) {
            image = "";
        } else {
            image = profile.getImage().toString();
        }
        //создаем запись о клиенте в tracking.client
        clientMaster = clientService.createClient(investId, ClientStatusType.registered, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(image), riskProfile);
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
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("result", new BigDecimal("0.2"));
        feeRateProperties.put("management", new BigDecimal("0.04"));
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
            .setScore(1)
            .setFeeRate(feeRateProperties)
            .setOverloaded(overloaded);
        strategyMaster = trackingService.saveStrategy(strategyMaster);
    }





    //Метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWintContractAndStrategy11(String SIEBLE_ID, UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                                    UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                                    ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                                    StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {
        //находим данные по клиенту в БД social
        String image = "";
        profile = profileService.getProfileBySiebelId(SIEBLE_ID);
        if (profile.getImage() == null) {
            image = "";
        }
        else {
            image = profile.getImage().toString();
        }
        //создаем запись о клиенте в tracking.client
        clientMaster = clientService.createClient1(investId, ClientStatusType.registered, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(image), riskProfile);
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
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("result", new BigDecimal("0.2"));
        feeRateProperties.put("management", new BigDecimal("0.04"));
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
            .setScore(1)
            .setFeeRate(feeRateProperties);
        strategyMaster = trackingService.saveStrategy(strategyMaster);
    }

    //Метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWintContractAndStrategyFee(String SIEBLE_ID, UUID investId, ClientRiskProfile riskProfile,String contractId, ContractRole contractRole, ContractState contractState,
                                                       UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                                       ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                                       StrategyStatus strategyStatus, int slaveCount, LocalDateTime date, String result, String management, Boolean overloaded) {

        //находим данные по клиенту в БД social
        String image = "";
        profile = profileService.getProfileBySiebelId(SIEBLE_ID);
        if (profile.getImage() == null) {
            image = "";
        } else {
            image = profile.getImage().toString();
        }
        clientMaster = clientService.createClient(investId, ClientStatusType.registered, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(image), riskProfile);
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
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("result", new BigDecimal(result));
        feeRateProperties.put("management", new BigDecimal(management));
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
            .setScore(1)
            .setFeeRate(feeRateProperties)
            .setOverloaded(overloaded);
        strategyMaster = trackingService.saveStrategy(strategyMaster);
    }


    //Метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWintContractAndStrategyWithProfile(String SIEBLE_ID, UUID investId, ClientRiskProfile riskProfile,String contractId, ContractRole contractRole, ContractState contractState,
                                                               UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                                               ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                                               StrategyStatus strategyStatus, int slaveCount, LocalDateTime date, Integer score, Boolean overload) {
//        //находим данные по клиенту в БД social
        String image = "";
        profile = profileService.getProfileBySiebelId(SIEBLE_ID);
        if (profile.getImage() == null) {
            image = "";
        } else {
            image = profile.getImage().toString();
        }
        //создаем запись о клиенте в tracking.client
        clientMaster = clientService.createClient(investId, ClientStatusType.registered, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(image), riskProfile);
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
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("result", new BigDecimal("0.2"));
        feeRateProperties.put("management", new BigDecimal("0.04"));
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
            .setScore(score)
            .setFeeRate(feeRateProperties)
            .setOverloaded(overload);
        strategyMaster = trackingService.saveStrategy(strategyMaster);
    }


    //Метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createClientWintContractAndStrategyWithOutProfile(UUID investId, ClientRiskProfile riskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                                                  UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                                                  ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                                                  StrategyStatus strategyStatus, int slaveCount, LocalDateTime date, String result, String management, Boolean overloaded) {


        clientMaster = clientService.createClient(investId, ClientStatusType.registered, null, riskProfile);
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
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("result", new BigDecimal(result));
        feeRateProperties.put("management", new BigDecimal(management));
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
            .setScore(1)
            .setFeeRate(feeRateProperties)
            .setOverloaded(overloaded);
        strategyMaster = trackingService.saveStrategy(strategyMaster);
    }

    //Метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public Client createClientWithProfile(String SIEBLE_ID, UUID investId) {
//        //находим данные по клиенту в БД social
        String image = "";
        profile = profileService.getProfileBySiebelId(SIEBLE_ID);
        if (profile.getImage() == null) {
            image = "";
        } else {
            image = profile.getImage().toString();
        }
        //создаем запись о клиенте в tracking.client
        clientMaster = clientService.createClient(investId, ClientStatusType.registered, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(image), null);
        return clientMaster;

    }

    //Метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createContractAndStrategy(Client clientMaster, String contractId, ContractRole contractRole, ContractState contractState,
                                          UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                          ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                          StrategyStatus strategyStatus, int slaveCount, LocalDateTime date, Integer score) {

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
        Map<String, BigDecimal> feeRateProperties = new HashMap<>();
        feeRateProperties.put("result", new BigDecimal("0.2"));
        feeRateProperties.put("management", new BigDecimal("0.04"));
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
            .setScore(score)
            .setFeeRate(feeRateProperties);
        strategyMaster = trackingService.saveStrategy(strategyMaster);
    }

    //Метод создает клиента, договор и стратегию в БД автоследования
    @Step("Создать договор и стратегию в бд автоследования для клиента {client}")
    @SneakyThrows
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createContractAndStrategyDraft(Client clientMaster, String contractId, ContractRole contractRole, ContractState contractState,
                                               UUID strategyId, String title, String description, StrategyCurrency strategyCurrency,
                                               ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile strategyRiskProfile,
                                               StrategyStatus strategyStatus, int slaveCount, LocalDateTime date) {

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
            .setActivationTime(date);

        strategyMaster = trackingService.saveStrategy(strategyMaster);
    }


    //Метод находит подходящий siebelId в сервисе счетов и Создаем запись по нему в табл. tracking.client
    public void createClient(UUID investId, ClientStatusType clientStatusType, SocialProfile socialProfile, ClientRiskProfile riskProfile) {
        clientMaster = clientService.createClient(investId, clientStatusType, socialProfile, riskProfile);
    }


    //метод создает клиента
    public void createClient(String SIEBLE_ID, UUID investId, ClientStatusType clientStatusType, ClientRiskProfile riskProfile) {
        //находим данные по клиенту в БД social
        profile = profileService.getProfileBySiebelId(SIEBLE_ID);
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, clientStatusType, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString()), riskProfile);
    }

    //метод создает клиента c договором
    public void createClientWithContract(String SIEBLE_ID, UUID investId, ClientStatusType clientStatusType, ClientRiskProfile riskProfile,
                                         String contractId, ContractRole contractRole, ContractState contractState,
                                         UUID strategyId) {
        //находим данные по клиенту в БД social
//        profile = profileService.getProfileBySiebelId(SIEBLE_ID);
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient(investId, clientStatusType, new SocialProfile()
            .setId(profile.getId().toString())
            .setNickname(profile.getNickname())
            .setImage(profile.getImage().toString()), riskProfile);
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


    //создаем портфель master в cassandra с позицией
    public void createMasterPortfolio(String contractIdMaster, UUID strategyId, List<MasterPortfolio.Position> positionList,
                                      int version, String money, Date date) {
        //базовая валюта
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolioWithChangedAt(contractIdMaster, strategyId, version,
            baseMoneyPosition, date, positionList);
    }


    public UUID uuid(ByteString bytes) {
        ByteBuffer buff = bytes.asReadOnlyByteBuffer();
        return new UUID(buff.getLong(), buff.getLong());
    }


    public String getPriceFromExchangePositionPriceCache(String ticker, String tradingClearingAccount, String type, String siebelId) {
        String price = "";
        //получаем содержимое кеша exchangePositionPriceCache
        List<Entity> resCachePrice = cacheApi.getAllEntities()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .reqSpec(r -> r.addHeader("x-tcs-siebel-id", siebelId))
            .cacheNamePath("exchangePositionPriceCache")
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .executeAs(validatedWith(shouldBeCode(SC_OK)));
       //отбираем данные по ticker+tradingClearingAccount+type
        List<Entity> prices = resCachePrice.stream()
            .filter(pr -> {
                    @SuppressWarnings("unchecked")
                    var keys = (Map<String, String>) pr.getKey();
                    return keys.get("ticker").equals(ticker)
                        && keys.get("tradingClearingAccount").equals(tradingClearingAccount)
                        && keys.get("priceType").equals(type);
                }
            )
            .collect(Collectors.toList());
        //достаем значение price
        @SuppressWarnings("unchecked")
        var values = (Map<Double, Object>) prices.get(0).getValue();
        price = values.get("price").toString();
        return price;
    }


    public List<String> getPriceFromExchangePositionCache(String ticker, String tradingClearingAccount, String siebelId) {
        String aciValue = "";
        String nominal = "";
        List<String> dateBond = new ArrayList<>();
        //получаем содержимое кеша exchangePositionCache
        List<Entity> resCacheExchangePosition = cacheApi.getAllEntities()
            .reqSpec(r -> r.addHeader("api-key", "tracking"))
            .reqSpec(r -> r.addHeader("x-tcs-siebel-id", siebelId))
            .cacheNamePath("exchangePositionCache")
            .xAppNameHeader("tracking")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .executeAs(validatedWith(shouldBeCode(SC_OK)));
        //отбираем данные по ticker+tradingClearingAccount+type
        List<Entity> position = resCacheExchangePosition.stream()
            .filter(pr -> {
                    @SuppressWarnings("unchecked")
                    var keys = (Map<String, String>) pr.getKey();
                    return keys.get("ticker").equals(ticker)
                        && keys.get("tradingClearingAccount").equals(tradingClearingAccount);
                }
            )
            .collect(Collectors.toList());
        //достаем значение price
        @SuppressWarnings("unchecked")
        var values = (Map<String, Object>) position.get(0).getValue();
        aciValue = values.get("aciValue").toString();
        nominal = values.get("nominal").toString();
        dateBond.add(aciValue);
        dateBond.add(nominal);
        return dateBond;
    }


    //создаем портфель master в cassandra
    public void createMasterPortfolioWithChangedAt(String contractIdMaster, UUID strategyId, List<MasterPortfolio.Position> positionList, int version, String money, Date date) {
        //с базовой валютой
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolioWithChangedAt(contractIdMaster, strategyId, version, baseMoneyPosition, date, positionList);
    }

    public List<MasterPortfolio.Position> masterOnePositions(Date date, String ticker, String tradingClearingAccount,
                                                             String quantity) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .quantity(new BigDecimal(quantity))
            .changedAt(date)
            .lastChangeDetectedVersion(3)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        return positionList;
    }


    public List<MasterPortfolio.Position> masterTwoPositions(Date date, String ticker1, String tradingClearingAccount1,
                                                             String quantity1, String ticker2, String tradingClearingAccount2,
                                                             String quantity2) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .changedAt(date)
            .lastChangeDetectedVersion(null)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantity2))
            .changedAt(date)
            .lastChangeDetectedVersion(null)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        return positionList;
    }


    public List<MasterPortfolio.Position> masterThreePositions(Date date, String ticker1, String tradingClearingAccount1,
                                                               String quantity1, String ticker2, String tradingClearingAccount2,
                                                               String quantity2, String ticker3, String tradingClearingAccount3,
                                                               String quantity3) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker1)
            .tradingClearingAccount(tradingClearingAccount1)
            .quantity(new BigDecimal(quantity1))
            .changedAt(date)
            .lastChangeDetectedVersion(null)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker2)
            .tradingClearingAccount(tradingClearingAccount2)
            .quantity(new BigDecimal(quantity2))
            .changedAt(date)
            .lastChangeDetectedVersion(null)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(ticker3)
            .tradingClearingAccount(tradingClearingAccount3)
            .quantity(new BigDecimal(quantity3))
            .changedAt(date)
            .lastChangeDetectedVersion(null)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        return positionList;
    }

    public BigDecimal valuePosBonds(String priceTs, String nominal, BigDecimal minPriceIncrement,
                                    String aciValue) {
        BigDecimal priceBefore = new BigDecimal(priceTs).multiply(new BigDecimal(nominal))
            .scaleByPowerOfTen(-2);
        BigDecimal minPriceIncrementNew = minPriceIncrement
            .multiply(new BigDecimal(nominal))
            .scaleByPowerOfTen(-2);
        BigDecimal roundPrice = priceBefore.divide(minPriceIncrementNew, 0, RoundingMode.HALF_UP)
            .multiply(minPriceIncrementNew);
        BigDecimal price = roundPrice
            .add(new BigDecimal(aciValue));
        return price;
    }


    public byte[] bytes(UUID uuid) {
        return ByteBuffer.allocate(16)
            .putLong(uuid.getMostSignificantBits())
            .putLong(uuid.getLeastSignificantBits())
            .array();
    }


    public ByteString byteString(UUID uuid) {
        return ByteString.copyFrom(bytes(uuid));
    }


    public Tracking.Portfolio.Position createPosAction(Tracking.Portfolio.Action action) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        return positionAction;
    }




    // создаем портфель ведущего с позициями в кассандре
    @Step("Создать договор и стратегию в бд автоследования для ведущего клиента {client}")
    @SneakyThrows
    public void createMasterPortfolioOnePosition(int days, int version, String money, String contractIdMaster, UUID strategyId) {
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
        Date date = Date.from(utc.toInstant());
    }



    // создаем портфель ведущего с позициями в кассандре
    public void createMasterPortfolioWithOutPosition(int days, int version, String money, String contractIdMaster, UUID strategyId) {
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        OffsetDateTime timeChangedAt = OffsetDateTime.now().minusDays(days);
        Date changedAt = Date.from(timeChangedAt.toInstant());
        createMasterPortfolioWithChangedAt(contractIdMaster, strategyId, positionListMaster, version, money, changedAt);
    }




    public String getPriceFromMarketData(String instrumentId, String type) {
        Response res = pricesApi.mdInstrumentPrices()
            .instrumentIdPath(instrumentId)
            .requestIdQuery("111")
            .systemCodeQuery("111")
            .typesQuery(type)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String price = res.getBody().jsonPath().getString("prices.price_value[0]");
        return price;
    }

    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    public void createEventInTrackingContractEvent(String contractIdSlave) {
        //создаем событие
        Tracking.Event event = createEventUpdateAfterSubscriptionSlave(contractIdSlave);
        log.info("Команда в tracking.contract.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.event
        kafkaSender.send(TRACKING_CONTRACT_EVENT, contractIdSlave, eventBytes);
    }

    // создаем команду в топик кафка tracking.master.command
    Tracking.Event createEventUpdateAfterSubscriptionSlave(String contractId) {
        OffsetDateTime now = OffsetDateTime.now();
        Tracking.Event event = Tracking.Event.newBuilder()
            .setId(com.google.protobuf.ByteString.copyFromUtf8(UUID.randomUUID().toString()))
            .setAction(Tracking.Event.Action.UPDATED)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(now.toEpochSecond())
                .setNanos(now.getNano())
                .build())
            .setContract(Tracking.Contract.newBuilder()
                .setId(contractId)
                .setState(Tracking.Contract.State.TRACKED)
                .setBlocked(false)
                .build())
            .build();
        return event;
    }
    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcription(UUID investId, ClientRiskProfile clientRiskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                  UUID strategyId, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
                                  java.sql.Timestamp dateEnd, Boolean blocked) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient1(investId, ClientStatusType.none, null, clientRiskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(false);
        contractSlave = contractService.saveContract(contractSlave);
        //создаем запись подписке клиента
        subscription = new Subscription()
            .setSlaveContractId(contractId)
            .setStrategyId(strategyId)
            .setStartTime(dateStart)
            .setStatus(subscriptionStatus)
            .setEndTime(dateEnd)
            .setBlocked(blocked);
        subscription = subscriptionService.saveSubscription(subscription);

    }

    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcriptionDraftOrInActive(UUID investId, ClientRiskProfile clientRiskProfile, String contractId, ContractRole contractRole, ContractState contractState,
                                  UUID strategyId, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
                                  java.sql.Timestamp dateEnd, Boolean blocked) throws JsonProcessingException {
        //создаем запись о клиенте в tracking.client
        clientSlave = clientService.createClient1(investId, ClientStatusType.none, null, clientRiskProfile);
        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(clientSlave.getId())
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(null)
            .setBlocked(false);
        contractSlave = contractService.saveContract(contractSlave);
        //создаем запись подписке клиента
        subscription = new Subscription()
            .setSlaveContractId(contractId)
            .setStrategyId(strategyId)
            .setStartTime(dateStart)
            .setStatus(subscriptionStatus)
            .setEndTime(dateEnd)
            .setBlocked(blocked);
        subscription = subscriptionService.saveSubscription(subscription);

    }



    //метод создает клиента, договор и стратегию в БД автоследования
    public void createSubcriptionNotClient(UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
                                  UUID strategyId, SubscriptionStatus subscriptionStatus,  java.sql.Timestamp dateStart,
                                  java.sql.Timestamp dateEnd, Boolean blocked) throws JsonProcessingException {

        // создаем запись о договоре клиента в tracking.contract
        contractSlave = new Contract()
            .setId(contractId)
            .setClientId(investId)
            .setRole(contractRole)
            .setState(contractState)
            .setStrategyId(strategyId)
            .setBlocked(false);
        contractSlave = contractService.saveContract(contractSlave);
        //создаем запись подписке клиента
        subscription = new Subscription()
            .setSlaveContractId(contractId)
            .setStrategyId(strategyId)
            .setStartTime(dateStart)
            .setStatus(subscriptionStatus)
            .setEndTime(dateEnd)
            .setBlocked(blocked);
        subscription = subscriptionService.saveSubscription(subscription);

    }

    //вызываем метод CreateSubscription для slave
    public void createSubscriptionSlave(String siebleIdSlave, String contractIdSlave, UUID strategyId) {
        subscriptionApi.createSubscription()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebleIdSlave)
            .contractIdQuery(contractIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        assertThat("ID стратегию не равно", subscription.getStrategyId(), is(strategyId));
        assertThat("статус подписки не равен", subscription.getStatus().toString(), is("active"));
        contractSlave = contractService.getContract(contractIdSlave);
    }

    //вызываем метод blockContract для slave
    public void BlockContract(String contractIdSlave) {
        contractApi.blockContract()
            .reqSpec(r -> r.addHeader("X-API_KEY", "tracking"))
            .xAppNameHeader("invest")
            .xTcsLoginHeader("tracking")
            .contractIdPath(contractIdSlave)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(ResponseBodyData::asString);
        contractSlave = contractService.getContract(contractIdSlave);

    }



//    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
//    public void createEventInTrackingContractEvent(String contractIdSlave)  {
//        //создаем событие
//        Tracking.Event event = createEventUpdateAfterSubscriptionSlave(contractIdSlave);
//        log.info("Команда в tracking.event:  {}", event);
//        //кодируем событие по protobuf схеме и переводим в byteArray
//        byte[] eventBytes = event.toByteArray();
//        //отправляем событие в топик kafka tracking.slave.command
//        kafkaSender.send(Topics.TRACKING_CONTRACT_EVENT, contractIdSlave, eventBytes);
//    }

//    // создаем команду в топик кафка tracking.event
//    public Tracking.Event createEventUpdateAfterSubscriptionSlave(String contractId) {
//        OffsetDateTime now = OffsetDateTime.now();
//        Tracking.Event event = Tracking.Event.newBuilder()
//            .setId(com.google.protobuf.ByteString.copyFromUtf8(UUID.randomUUID().toString()))
//            .setAction(Tracking.Event.Action.UPDATED)
//            .setCreatedAt(Timestamp.newBuilder()
//                .setSeconds(now.toEpochSecond())
//                .setNanos(now.getNano())
//                .build())
//            .setContract(Tracking.Contract.newBuilder()
//                .setId(contractId)
//                .setState(Tracking.Contract.State.UNTRACKED)
//                .setBlocked(false)
//                .build())
//            .build();
//        return event;
//    }

//    public void createSubcriptionBlock(long SubscriptionId, SubscriptionBlockReason subscriptionBlockReason, Range range) throws JsonProcessingException {
//        subscriptionBlock = new SubscriptionBlock()
//            .setSubscriptionId(SubscriptionId)
//            .setReason(subscriptionBlockReason)
//            .setPeriod(range);
//        subscriptionBlock = subscriptionBlockService.saveSubscriptionBlock(subscriptionBlock);

//    }

}
