package stpTrackingApi.getStrategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreator;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.*;
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.model.GetStrategyResponse;
import ru.qa.tinkoff.swagger.tracking.model.GetStrategyResponseCharacteristics;
import ru.qa.tinkoff.swagger.tracking.model.PortfolioRateSection;
import ru.qa.tinkoff.swagger.tracking.model.StrategyCharacteristic;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.SubscriptionBlock;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("getStrategy - Получение информации по торговой стратегии")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@Tags({@Tag("stp-tracking-api"), @Tag("getStrategy")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    ApiCreatorConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    StpTrackingSiebelConfiguration.class
})
public class GetStrategyTest {
    @Autowired
    StrategyService strategyService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    SubscriptionService subscriptionService;
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    MasterPortfolioValueDao masterPortfolioValueDao;
    @Autowired
    MasterPortfolioMaxDrawdownDao masterPortfolioMaxDrawdownDao;
    @Autowired
    MasterPortfolioPositionRetentionDao masterPortfolioPositionRetentionDao;
    @Autowired
    MasterPortfolioRateDao masterPortfolioRateDao;
    @Autowired
    MasterPortfolioTopPositionsDao masterPortfolioTopPositionsDao;
    @Autowired
    MasterSignalDao masterSignalDao;
    @Autowired
    SignalFrequencyDao signalFrequencyDao;
    @Autowired
    SignalsCountDao signalsCountDao;
    @Autowired
    StrategyTailValueDao strategyTailValueDao;
    @Autowired
    SubscriptionBlockService subscriptionBlockService;
    @Autowired
    ApiCreator<StrategyApi> strategyApiCreator;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel stpSiebel;


    Client clientMaster;
    Strategy strategyMaster;
    Client clientSlave;
    Subscription subscription;
    SubscriptionBlock subscriptionBlock;
    Strategy strategy;
    MasterPortfolioMaxDrawdown masterPortfolioMaxDrawdown;
    MasterPortfolioPositionRetention masterPortfolioPositionRetention;
    MasterPortfolioRate masterPortfolioRate;
    MasterPortfolioTopPositions masterPortfolioTopPositions;
    SignalFrequency signalFrequency;
    SignalsCount signalsCount;
    StrategyTailValue strategyTailValue;
    String contractIdMaster;
    String contractIdSlave;
    UUID strategyId;
    String siebelIdMaster;
    String siebelIdSlave;
    MasterPortfolioValue masterPortfolioValue;

    String quantitySBER = "30";
    String quantityFXDE = "5";
    String quantitySU29009RMFS6 = "7";
    String quantityUSD = "2000";
    String expectedRelativeYieldName = "expected-relative-yield";
    String description = "стратегия autotest GetStrategyTest";

    @BeforeAll
    void conf() {
        siebelIdMaster = stpSiebel.siebelIdApiMaster;
        siebelIdSlave = stpSiebel.siebelIdApiSlave;
    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionBlockService.deleteSubscription(subscriptionBlock);
            } catch (Exception e) {
            }

            try {
                subscriptionService.deleteSubscription(steps.subscription);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(steps.contractSlave);
            } catch (Exception e) {
            }
            try {
                clientSlave = clientService.getClient(steps.clientSlave.getId());
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(steps.clientSlave);
            } catch (Exception e) {
            }
            try {
                trackingService.deleteStrategy(steps.strategyMaster);
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
                masterPortfolioDao.deleteMasterPortfolio(contractIdMaster, strategyId);
            } catch (Exception e) {
            }
            try {
                masterPortfolioValueDao.deleteMasterPortfolioValueByStrategyId(strategyId);
            } catch (Exception e) {
            }
            try {
                masterPortfolioMaxDrawdownDao.deleteMasterPortfolioMaxDrawdownByStrategyId(strategyId);
            } catch (Exception e) {
            }
            try {
                masterPortfolioPositionRetentionDao.deleteMasterPortfolioPositionRetention(strategyId);
            } catch (Exception e) {
            }
            try {
                masterPortfolioRateDao.deleteMasterPortfolioRateByStrategyId(strategyId);
            } catch (Exception e) {
            }
            try {
                masterPortfolioTopPositionsDao.deleteMasterPortfolioTopPositionsByStrategyId(strategyId);
            } catch (Exception e) {
            }
            try {
                masterSignalDao.deleteMasterSignalByStrategy(strategyId);
            } catch (Exception e) {
            }
            try {
                signalFrequencyDao.deleteSignalFrequencyByStrategyId(strategyId);
            } catch (Exception e) {
            }
            try {
                signalsCountDao.deleteSignalsCountByStratedyId(strategyId);
            } catch (Exception e) {
            }
            try {
                strategyTailValueDao.deleteStrategyTailValueByStrategyId(strategyId);
            } catch (Exception e) {
            }
            try {
                steps.createEventInTrackingContractEvent(contractIdMaster);
            } catch (Exception e) {
            }
            try {
                steps.createEventInTrackingContractEvent(contractIdSlave);
            } catch (Exception e) {
            }
        });
    }

    @Test
    @AllureId("1583843")
    @DisplayName("C1583843.GetStrategy.Получение данных торговой стратегии c типом характеристик условий management-fee")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1583843() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //создаем запись в кассандре
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        createMasterPortfolio(contractIdMaster, strategyId, 1, "6259.17", positionList);
        //GetStrategyResponse getStrategy = apiClient.strategy().getStrategy()
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategyMaster = strategyService.getStrategy(strategyId);
        List<GetStrategyResponseCharacteristics> strategyCharacteristics =
            getStrategy.getCharacteristics().stream()
                .filter(strategyCharacteristic -> strategyCharacteristic.getType().getValue().equals("condition"))
                .collect(Collectors.toList());

        //Проверяем, что не нашли characteristics.expected-relative-yield (получим false)
        Boolean checkStrategyCharacteristicsExpectedRelativeYield =
            getStrategy.getCharacteristics().get(0).getItems().stream()
                .anyMatch(strategyCharacteristic -> strategyCharacteristic.getId().equals("expected-relative-yield"));
        BigDecimal managementFee = new BigDecimal("0.05")
            .multiply(BigDecimal.valueOf(100))
            .divide(new BigDecimal("12"), 3, BigDecimal.ROUND_HALF_UP);
        assertThat("title не равно", strategyCharacteristics.get(0).getItems().get(0).getTitle(), is("Комиссия за управление"));
        assertThat("Тип характеристики не равно", strategyCharacteristics.get(0).getType(), is(GetStrategyResponseCharacteristics.TypeEnum.CONDITION));
        assertThat("title не равно", strategyCharacteristics.get(0).getItems().get(0).getTitle(), is("Комиссия за управление"));
        assertThat("value не равно", strategyCharacteristics.get(0).getItems().get(0).getValue(), is(managementFee.toString() + " %"));
        assertThat("title не равно", strategyCharacteristics.get(0).getItems().get(1).getTitle(), is("Комиссия за результат"));
        assertThat("Получили expected-relative-yield", checkStrategyCharacteristicsExpectedRelativeYield, is(false));
        assertThat("value не равно", strategyCharacteristics.get(0).getItems().get(1).getValue(), is("30.0 %"));
        assertThat("isOverload != false", getStrategy.getIsOverloaded(), is(false));
    }

    @Test
    @AllureId("945710")
    @DisplayName("C945710.GetStrategy.Получение данных торговой стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C945710() throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2, LocalDateTime.now(), 1, "0.3", "0.05", true, null, "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(5).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = createListMasterPosition(date, 5,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        createMasterPortfolio(contractIdMaster, strategyId, 6, "6259.17", positionList);
        //создаем запись о стоимости портфеля
        createDateMasterPortfolioValue(strategyId, 4, 2, "683491.11");
        createDateMasterPortfolioValue(strategyId, 3, 5, "87269.99");
        createDateMasterPortfolioValue(strategyId, 2, 4, "982684.75");
        createDateMasterPortfolioMaxDrawdown(strategyId, 1, 2, "12");
        createDateMasterPortfolioPositionRetention(strategyId, 1, 2, "weeks");
        createDateMasterPortfolioRate(strategyId, 1, 2);
        createDateMasterPortfolioTopPositions(strategyId, 1, 2);
        createTestDateToMasterSignal(strategyId);
        createDateSignalFrequency(strategyId, 1, 2, 6);
        createDateSignalsCount(strategyId, 1, 2, 6);
        createDateStrategyTailValue(strategyId, 1, 2, "500.0");
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategyMaster = strategyService.getStrategy(strategyId);
        assertThat("Id стратегии не равно", getStrategy.getId(), is(strategyMaster.getId()));
        assertThat("status не равно", getStrategy.getStatus().toString(), is(strategyMaster.getStatus().toString()));
        assertThat("title не равно", getStrategy.getTitle(), is(strategyMaster.getTitle()));
        assertThat("description не равно", getStrategy.getDescription(), is(strategyMaster.getDescription()));
        assertThat("riskProfile не равно", getStrategy.getRiskProfile().toString(), is(strategyMaster.getRiskProfile().toString()));
        assertThat("isOverloaded != true", getStrategy.getIsOverloaded(), is(true));
    }


    @Test
    @AllureId("1271501")
    @DisplayName("C1271501.GetStrategy.Получение данных торговой стратегии draft")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1271501() throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.3", "0.05", false, null,
            "TEST", "TEST11");
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        createMasterPortfolio(contractIdMaster, strategyId, 1, "6259.17", positionList);
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategyMaster = strategyService.getStrategy(strategyId);
        List<StrategyCharacteristic> recommendedBaseMoney = getStrategyCharacteristic(getStrategy, "main", "recommended-base-money-position-quantity");
        List<StrategyCharacteristic> baseCurrency = getStrategyCharacteristic(getStrategy, "main", "base-currency");
        List<StrategyCharacteristic> tailValue = getStrategyCharacteristic(getStrategy, "main", "tail-value");
        List<StrategyCharacteristic> slavesCount = getStrategyCharacteristic(getStrategy, "main", "slaves-count");
        List<StrategyCharacteristic> activationInterval = getStrategyCharacteristic(getStrategy, "main", "activation-interval");
        List<StrategyCharacteristic> signalsCount = getStrategyCharacteristic(getStrategy, "main", "signals-count");
        List<StrategyCharacteristic> maxDrawdown = getStrategyCharacteristic(getStrategy, "main", "master-portfolio-max-drawdown");
        List<StrategyCharacteristic> signalFrequency = getStrategyCharacteristic(getStrategy, "main", "signal-frequency");
        List<StrategyCharacteristic> positionRetention = getStrategyCharacteristic(getStrategy, "main", "master-portfolio-position-retention");
        List<PortfolioRateSection> portfolioRateTypes = getStrategy.getPortfolioRate().getTypes();
        List<PortfolioRateSection> portfolioRateTSectors = getStrategy.getPortfolioRate().getSectors();
        List<PortfolioRateSection> portfolioRateTCompanies = getStrategy.getPortfolioRate().getCompanies();
        List<ru.qa.tinkoff.swagger.tracking.model.TopPosition> topPositions = getStrategy.getTopPositions();
        assertThat("Id стратегии не равно", getStrategy.getId(), is(strategyMaster.getId()));
        assertThat("status не равно", getStrategy.getStatus().toString(), is(strategyMaster.getStatus().toString()));
        assertThat("title не равно", getStrategy.getTitle(), is(strategyMaster.getTitle()));
        assertThat("description не равно", getStrategy.getDescription(), is(strategyMaster.getDescription()));
        assertThat("riskProfile не равно", getStrategy.getRiskProfile().toString(), is(strategyMaster.getRiskProfile().toString()));
        assertThat("recommended-base-money-position-quantity не равно", recommendedBaseMoney.size(), is(0));
        checkCharacteristics(baseCurrency, "base-currency", "Валюта", "Рубли");
        assertThat("tail-value не равно", tailValue.size(), is(0));
        checkCharacteristics(slavesCount, "slaves-count", "Количество подписчиков", "0");
        assertThat("activation-interval не равно", activationInterval.size(), is(0));
        assertThat("signalsCount не равно", signalsCount.size(), is(0));
        assertThat("master-portfolio-max-drawdown не равно", maxDrawdown.size(), is(0));
        assertThat("signal-frequency не равно", signalFrequency.size(), is(0));
        assertThat("master-portfolio-position-retention не равно", positionRetention.size(), is(0));
        assertThat("positionRetention Sectors Title не равно", portfolioRateTSectors.get(0).getTitle(), is("Денежные средства"));
        assertThat("positionRetention Sectors Percent не равно", portfolioRateTSectors.get(0).getPercent().toString(), is("100.00"));
        assertThat("positionRetention Types Title не равно", portfolioRateTypes.get(0).getTitle(), is("Денежные средства"));
        assertThat("positionRetention Types Percent не равно", portfolioRateTypes.get(0).getPercent().toString(), is("100.00"));
        assertThat("positionRetention Companies Title не равно", portfolioRateTCompanies.get(0).getTitle(), is("Денежные средства"));
        assertThat("positionRetention Companies Percent не равно", portfolioRateTCompanies.get(0).getPercent().toString(), is("100.00"));
        assertThat("topPositions не равно", topPositions.size(), is(0));
        clientMaster = clientService.getClient(investIdMaster);
        assertThat("profileId не равно", getStrategy.getOwner().getSocialProfile().getId().toString(), is(clientMaster.getSocialProfile().getId()));
        //Находим в БД автоследования подписчиков стратегии
        assertThat("Признак наличия подписки авторизованного пользователя на стратегию не равно", getStrategy.getSubscription().getIsLead(), is(false));
        assertThat("Статус блокировки не равно", getStrategy.getSubscription().getBlocked(), is(false));
        assertThat("Причина блокировки не равно", getStrategy.getSubscription().getBlockReasons().size(), is(0));
    }


    private static Stream<Arguments> provideSubscriptionStatusAndStrategyStatus() {
        return Stream.of(
            Arguments.of(SubscriptionStatus.draft, StrategyStatus.active),
            Arguments.of(SubscriptionStatus.active, StrategyStatus.frozen),
            Arguments.of(SubscriptionStatus.draft, StrategyStatus.frozen),
            Arguments.of(SubscriptionStatus.active, StrategyStatus.active)
        );
    }

    @ParameterizedTest
    @MethodSource("provideSubscriptionStatusAndStrategyStatus")
    @AllureId("946707")
    @DisplayName("C946707.GetStrategy.Получение данных торговой стратегии, проверка подписки на стратегию авторизованного пользователя")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C946707(SubscriptionStatus subscriptionStatus, StrategyStatus strategyStatus) throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            strategyStatus, 1, LocalDateTime.now(), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(5).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        createMasterPortfolio(contractIdMaster, strategyId, 1, "6259.17", positionList);
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(1);
        steps.createSubcription(investIdSlave, ClientRiskProfile.conservative, contractIdSlave, ContractState.tracked,
            strategyId, subscriptionStatus, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //Находим в БД автоследования подписчиков стратегии
        assertThat("Номер договора, с которого подписан ведомый", getStrategy.getSubscription().getContractId(), is(contractIdSlave));
        assertThat("Признак наличия подписки авторизованного пользователя на стратегию не равно", getStrategy.getSubscription().getIsLead(), is(true));
    }


//    @Test
//    @AllureId("1270200")
//    @DisplayName("C1270200.GetStrategy.Получение данных торговой стратегии, проверка подписки на стратегию авторизованного пользователя. переподписанный пользователь")
//    @Subfeature("Успешные сценарии")
//    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
//    void C1270200() throws JsonProcessingException {
//        int randomNumber = 0 + (int) (Math.random() * 100);
//        String title = "Autotest" +String.valueOf(randomNumber);
//        String description = "new test стратегия autotest";
//        strategyId = UUID.randomUUID();
//        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
//        Date date = Date.from(utc.toInstant());
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
//        UUID investIdMaster = resAccountMaster.getInvestId();
//        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
//        //получаем данные по клиенту master в api сервиса счетов
//        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
//        UUID investIdSlave = resAccountSlave.getInvestId();
//        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
//
//        //создаем в БД tracking данные: client, contract, strategy в статусе active
//        steps.createClientWintContractAndStrategyFee(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
//            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
//            StrategyStatus.active, 1,LocalDateTime.now().minusDays(5).minusHours(2), "0.3", "0.05");
//
//        //создаем запись  протфеле в кассандре
//        List<MasterPortfolio.Position> positionList = new ArrayList<>();
//        createMasterPortfolio(contractIdMaster, strategyId, 1, "6259.17", positionList);
//
//        subscriptionApi.createSubscription()
//            .xAppNameHeader("invest")
//            .xAppVersionHeader("4.5.6")
//            .xPlatformHeader("ios")
//            .xTcsSiebelIdHeader(siebelIdSlave)
//            .contractIdQuery(contractIdSlave)
//            .strategyIdPath(strategyId)
//            .respSpec(spec -> spec.expectStatusCode(200))
//            .execute(ResponseBodyData::asString);
//
//        subscriptionApi.deleteSubscription()
//            .xAppNameHeader("invest")
//            .xAppVersionHeader("4.5.6")
//            .xPlatformHeader("ios")
//            .xTcsSiebelIdHeader(siebelIdSlave)
//            .contractIdQuery(contractIdSlave)
//            .strategyIdPath(strategyId)
//            .respSpec(spec -> spec.expectStatusCode(200))
//            .execute(ResponseBodyData::asString);
//
//        subscriptionApi.createSubscription()
//            .xAppNameHeader("invest")
//            .xAppVersionHeader("4.5.6")
//            .xPlatformHeader("ios")
//            .xTcsSiebelIdHeader(siebelIdSlave)
//            .contractIdQuery(contractIdSlave)
//            .strategyIdPath(strategyId)
//            .respSpec(spec -> spec.expectStatusCode(200))
//            .execute(ResponseBodyData::asString);
//
//        GetStrategyResponse getStrategy = strategyApiSupplier.get().getStrategy()
//            .xAppNameHeader("invest")
//            .xAppVersionHeader("4.5.6")
//            .xPlatformHeader("ios")
//            .xTcsSiebelIdHeader(siebelIdSlave)
//            .strategyIdPath(strategyId)
//            .respSpec(spec -> spec.expectStatusCode(200))
//            .execute(response -> response.as(GetStrategyResponse.class));
//
//        //Находим в БД автоследования подписчиков стратегии
//        assertThat("Номер договора, с которого подписан ведомый", getStrategy.getSubscription().getContractId(), is(contractIdSlave));
//        assertThat("Признак наличия подписки авторизованного пользователя на стратегию не равно", getStrategy.getSubscription().getIsLead(), is(true));
//    }
//
//

    private static Stream<Arguments> provideSubscriptionStatus() {
        return Stream.of(
            Arguments.of(SubscriptionStatus.draft),
            Arguments.of(SubscriptionStatus.active)
        );
    }

    @ParameterizedTest
    @MethodSource("provideSubscriptionStatus")
    @AllureId("1250904")
    @DisplayName("C1250904.GetStrategy.Проверка blocked и blocked reason для подписок, status = 'active', blocked = true, period = now")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1250904(SubscriptionStatus subscriptionStatus) throws JsonProcessingException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 1, LocalDateTime.now().minusDays(5).minusHours(2), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        createMasterPortfolio(contractIdMaster, strategyId, 1, "6259.17", positionList);
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(1);
        steps.createSubcription(investIdSlave, ClientRiskProfile.conservative, contractIdSlave, ContractState.tracked,
            strategyId, subscriptionStatus, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, true, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String periodDefault = "[" + fmt.format(startSubTime) + ",)";
        subscriptionBlock = subscriptionBlockService.saveSubscriptionBlock(subscriptionId,
            SubscriptionBlockReason.RISK_PROFILE, periodDefault);
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //Находим в БД автоследования подписчиков стратегии
        assertThat("Номер договора, с которого подписан ведомый", getStrategy.getSubscription().getContractId(), is(contractIdSlave));
        assertThat("Признак наличия подписки авторизованного пользователя на стратегию не равно", getStrategy.getSubscription().getIsLead(), is(true));
        assertThat("Статус блокировки не равно", getStrategy.getSubscription().getBlocked(), is(true));
        assertThat("Причина блокировки не равно", getStrategy.getSubscription().getBlockReasons().get(0).getId(), is("risk-profile"));
    }


    @Test
    @AllureId("1250915")
    @DisplayName("C1250915.GetStrategy.Проверка blocked и blocked reason для подписок, status = 'active', blocked = true, period = past")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1250915() throws JsonProcessingException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 1, LocalDateTime.now().minusDays(5).minusHours(2), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        createMasterPortfolio(contractIdMaster, strategyId, 1, "6259.17", positionList);
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(4);
        OffsetDateTime endTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, ClientRiskProfile.conservative, contractIdSlave,  ContractState.tracked,
            strategyId, SubscriptionStatus.active, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, true, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String periodDefault = "[" + fmt.format(startSubTime) + "," + fmt.format(endTime) + "]";
        subscriptionBlock = subscriptionBlockService.saveSubscriptionBlock(subscriptionId,
            SubscriptionBlockReason.RISK_PROFILE, periodDefault);
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //Находим в БД автоследования подписчиков стратегии
        assertThat("Номер договора, с которого подписан ведомый", getStrategy.getSubscription().getContractId(), is(contractIdSlave));
        assertThat("Признак наличия подписки авторизованного пользователя на стратегию не равно", getStrategy.getSubscription().getIsLead(), is(true));
        assertThat("Статус блокировки не равно", getStrategy.getSubscription().getBlocked(), is(true));
        assertThat("Причина блокировки не равно", getStrategy.getSubscription().getBlockReasons().size(), is(0));
    }


    @Test
    @AllureId("1250886")
    @DisplayName("C1250886.GetStrategy.Проверка blocked и blocked reason для подписок, status = 'active'")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1250886() throws JsonProcessingException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountSlave = steps.getBrokerAccounts(siebelIdSlave);
        UUID investIdSlave = resAccountSlave.getInvestId();
        contractIdSlave = resAccountSlave.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 1, LocalDateTime.now().minusDays(5).minusHours(2), 1, "0.3", "0.05", true, null, "TEST", "TEST11");
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        createMasterPortfolio(contractIdMaster, strategyId, 1, "6259.17", positionList);
        OffsetDateTime startSubTime = OffsetDateTime.now().minusDays(4);
        OffsetDateTime endTime = OffsetDateTime.now().minusDays(3);
        steps.createSubcription(investIdSlave, ClientRiskProfile.conservative, contractIdSlave,  ContractState.tracked,
            strategyId, SubscriptionStatus.active, new Timestamp(startSubTime.toInstant().toEpochMilli()),
            null, false, false);
        subscription = subscriptionService.getSubscriptionByContract(contractIdSlave);
        long subscriptionId = subscription.getId();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String periodDefault = "[" + fmt.format(startSubTime) + "," + fmt.format(endTime) + "]";
        subscriptionBlock = subscriptionBlockService.saveSubscriptionBlock(subscriptionId,
            SubscriptionBlockReason.RISK_PROFILE, periodDefault);
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //Находим в БД автоследования подписчиков стратегии
        assertThat("Номер договора, с которого подписан ведомый", getStrategy.getSubscription().getContractId(), is(contractIdSlave));
        assertThat("Признак наличия подписки авторизованного пользователя на стратегию не равно", getStrategy.getSubscription().getIsLead(), is(true));
        assertThat("Статус блокировки не равно", getStrategy.getSubscription().getBlocked(), is(false));
        assertThat("Причина блокировки не равно", getStrategy.getSubscription().getBlockReasons().size(), is(0));
    }


    private static Stream<Arguments> provideScore() {
        return Stream.of(
            Arguments.of((Object) null),
            Arguments.of(1)
        );
    }


    @ParameterizedTest
    @MethodSource("provideScore")
    @AllureId("1223538")
    @DisplayName("C1223538.GetStrategy.Получение данных торговой стратегии,  необязательный параметр score")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1223538(Integer score) {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе draft
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, score, "0.2", "0.04", true, new BigDecimal(58.00), "TEST", "TEST11");
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //Находим в БД автоследования подписчиков стратегии
        assertThat("Оценка стратегии", getStrategy.getScore(), is(score));

    }


    @Test
    @AllureId("1217969")
    @DisplayName("C1217969.GetStrategy.Получение данных торговой стратегии.Нет подписки")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1217969() throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2, LocalDateTime.now(), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(5).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        createMasterPortfolio(contractIdMaster, strategyId, 1, "6259.17", positionList);
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //Проверяем признак нализчия подписчиков стратегии
        assertThat("Признак наличия подписки авторизованного пользователя на стратегию не равно", getStrategy.getSubscription().getIsLead(), is(false));
    }


    @Test
    @AllureId("1115576")
    @DisplayName("C1115576.GetStrategy.Не найдена стратегия в strategy")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1115576() throws JSONException {
        strategyId = UUID.randomUUID();
        String rawResponse = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(422))
            .execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(rawResponse);
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("StrategyNotFound"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Стратегия не найдена"));
    }


    private static Stream<Arguments> provideParamCurrency() {
        return Stream.of(
            Arguments.of(StrategyCurrency.rub, "₽", 5000),
            Arguments.of(StrategyCurrency.usd, "$", 100)

        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideParamCurrency")
    @AllureId("946709")
    @DisplayName("C946709.GetStrategy.Определяем характеристики стратегии: recommended-base-money-position-quantity")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C946709(StrategyCurrency strategyCurrency, String symbol, int multiplicity) throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, strategyCurrency, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2, LocalDateTime.now(), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(5).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = createListMasterPosition(date, 5,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        createMasterPortfolio(contractIdMaster, strategyId, 6, "6259.17", positionList);
        //создаем запись о стоимости портфеля
        createDateMasterPortfolioValue(strategyId, 4, 2, "683491.11");
        createDateMasterPortfolioValue(strategyId, 3, 5, "87269.99");
        createDateMasterPortfolioValue(strategyId, 2, 4, "982684.75");
        // вызываем метод getStrategy
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //выбираем проверяемую характеристику
        List<StrategyCharacteristic> characteristic = getStrategyCharacteristic(getStrategy, "main", "recommended-base-money-position-quantity");
        //расчитываем значение recommendedBaseMoney
        BigDecimal recommendedBaseMoneyPositionQuantity = new BigDecimal("982684.75")
            .add(new BigDecimal("982684.75").multiply(new BigDecimal("0.05")));
        recommendedBaseMoneyPositionQuantity = roundDecimal(recommendedBaseMoneyPositionQuantity, multiplicity);
        String str = String.format("%,d", recommendedBaseMoneyPositionQuantity.intValue());
        String rubbleSymbol = symbol;
        String recommendedBaseMoney = str.replace(",", " ") + " " + rubbleSymbol;
        //проверяем полученные данные с расчетами
        checkCharacteristics(characteristic, "recommended-base-money-position-quantity", "Рекомендуемая сумма", recommendedBaseMoney);
        assertThat("Подсказка не равно", characteristic.get(0).getHint(), is(""));
    }


    @Test
    @AllureId("946710")
    @DisplayName("C946710.GetStrategy.Определяем характеристики стратегии: base-currency")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C946710() throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2, LocalDateTime.now(), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(5).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        createMasterPortfolio(contractIdMaster, strategyId, 1, "6259.17", positionList);
        // вызываем метод getStrategy
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //смотрим характеристики и находим среди них base-currency
        List<StrategyCharacteristic> characteristic = getStrategyCharacteristic(getStrategy, "main", "base-currency");
        //проверяем полученные данные
        checkCharacteristics(characteristic, "base-currency", "Валюта", "Рубли");
    }


    private static Stream<Arguments> provideTailValue() {
        return Stream.of(
            Arguments.of(StrategyCurrency.usd, "523.12", "до 1 тыс. $"),
            Arguments.of(StrategyCurrency.usd, "1223", "до 10 тыс. $"),
            Arguments.of(StrategyCurrency.usd, "1000000", "до 5 млн. $"),
            Arguments.of(StrategyCurrency.rub, "523.12", "до 100 тыс. ₽"),
            Arguments.of(StrategyCurrency.rub, "10000000", "до 100 млн. ₽"),
            Arguments.of(StrategyCurrency.rub, "130101040", "до 500 млн. ₽"),
            Arguments.of(StrategyCurrency.rub, "1000000011", "более 1 млрд. ₽")
        );
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideTailValue")
    @AllureId("946711")
    @DisplayName("C946711.GetStrategy.Определяем характеристики стратегии: tail-value")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C946711(StrategyCurrency strategyCurrency, String tailValue, String result) throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, strategyCurrency, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2, LocalDateTime.now(), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(5).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = createListMasterPosition(date, 5,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        createMasterPortfolio(contractIdMaster, strategyId, 6, "6259.17", positionList);
        //создаем запись о стоимости портфеля
        createDateStrategyTailValue(strategyId, 0, 0, tailValue);
        // вызываем метод getStrategy
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //смотрим характеристики и находим среди них tail-value
        List<StrategyCharacteristic> characteristic = getStrategyCharacteristic(getStrategy, "main", "tail-value");
        //проверяем полученные данные с расчетами
        checkCharacteristics(characteristic, "tail-value", "Денег в управлении", result);
    }


    private static Stream<Arguments> provideSlavesCount() {
        return Stream.of(
            Arguments.of(15, "15"),
            Arguments.of(1223, "1" + "\u00A0" + "223"),
            Arguments.of(2345555, "2" + "\u00A0" + "345" + "\u00A0" + "555")
        );
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideSlavesCount")
    @AllureId("1260965")
    @DisplayName("C1260965.GetStrategy.Определяем характеристики стратегии: slaves-count")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1260965(int countSlaves, String result) throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, countSlaves, LocalDateTime.now(), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(5).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = createListMasterPosition(date, 5,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        createMasterPortfolio(contractIdMaster, strategyId, 6, "6259.17", positionList);
        // вызываем метод getStrategy
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //смотрим характеристики и находим среди них slaves-count
        List<StrategyCharacteristic> characteristic = getStrategyCharacteristic(getStrategy, "main", "slaves-count");
        //проверяем полученные данные с расчетами
        checkCharacteristics(characteristic, "slaves-count", "Количество подписчиков", result);
    }


    private static Stream<Arguments> provideActivationInterval() {
        return Stream.of(
            Arguments.of(5, "меньше месяца"),
            Arguments.of(45, "1 месяц"),
            Arguments.of(123, "4 месяца"),
            Arguments.of(380, "1 год"),
            Arguments.of(1234, "3 года 4 мес."),
            Arguments.of(2234, "6 лет 1 мес.")
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideActivationInterval")
    @AllureId("1261187")
    @DisplayName("C1261187.GetStrategy.Определяем характеристики стратегии: activation-interval")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1261187(int days, String result) throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2, LocalDateTime.now(), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(days).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = createListMasterPosition(date, 5,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        createMasterPortfolio(contractIdMaster, strategyId, 6, "6259.17", positionList);
        // вызываем метод getStrategy
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //смотрим характеристики и находим среди них "activation-interval"
        List<StrategyCharacteristic> characteristic = getStrategyCharacteristic(getStrategy, "main", "activation-interval");
        //проверяем полученные данные с расчетами
        checkCharacteristics(characteristic, "activation-interval", "Сколько существует", result);
    }


    private static Stream<Arguments> provideSignalsСount() {
        return Stream.of(
            Arguments.of(15, "15"),
            Arguments.of(1223, "1" + "\u00A0" + "223"),
            Arguments.of(2345555, "2" + "\u00A0" + "345" + "\u00A0" + "555")
        );
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideSignalsСount")
    @AllureId("1265507")
    @DisplayName("C1265507.GetStrategy.Определяем характеристики стратегии: signals-count")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1265507(int signalsСount, String result) throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2, LocalDateTime.now(), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(5).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = createListMasterPosition(date, 5,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        createMasterPortfolio(contractIdMaster, strategyId, 6, "6259.17", positionList);
        createDateSignalsCount(strategyId, 1, 2, signalsСount);
        // вызываем метод getStrategy
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //смотрим характеристики и находим среди них "signals-count"
        List<StrategyCharacteristic> characteristic = getStrategyCharacteristic(getStrategy, "trade", "signals-count");
        //проверяем полученные данные с расчетами
        checkCharacteristics(characteristic, "signals-count", "Общее количество сделок", result);
    }


    private static Stream<Arguments> provideMaxDrawdown() {
        return Stream.of(
            Arguments.of("0"),
            Arguments.of("5"),
            Arguments.of("74"),
            Arguments.of("100")
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideMaxDrawdown")
    @AllureId("1266065")
    @DisplayName("C1266065.GetStrategy.Определяем характеристики стратегии: master-portfolio-max-drawdown")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1266065(String value) throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2, LocalDateTime.now(), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(5).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = createListMasterPosition(date, 5,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        createMasterPortfolio(contractIdMaster, strategyId, 6, "6259.17", positionList);
        createDateMasterPortfolioMaxDrawdown(strategyId, 1, 2, value);
        BigDecimal maxDrawdownValue = new BigDecimal(value).multiply(new BigDecimal("-1"));
        String result = maxDrawdownValue.toString() + " %";
        // вызываем метод getStrategy
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //смотрим характеристики и находим среди них "master-portfolio-max-drawdown"
        List<StrategyCharacteristic> characteristic = getStrategyCharacteristic(getStrategy, "trade", "master-portfolio-max-drawdown");
        //проверяем полученные данные с расчетами
        checkCharacteristics(characteristic, "master-portfolio-max-drawdown", "Максимальная просадка", result);
    }


    private static Stream<Arguments> provideSignalFrequency() {
        return Stream.of(
            Arguments.of(0, "ежемесячно"),
            Arguments.of(2, "ежемесячно"),
            Arguments.of(7, "еженедельно"),
            Arguments.of(10, "еженедельно"),
            Arguments.of(11, "ежедневно"),
            Arguments.of(12, "ежедневно")
        );
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideSignalFrequency")
    @AllureId("1266087")
    @DisplayName("C1266087.GetStrategy.Определяем характеристики стратегии: signal-frequency")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1266087(int value, String result) throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2, LocalDateTime.now(), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(5).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = createListMasterPosition(date, 5,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        createMasterPortfolio(contractIdMaster, strategyId, 6, "6259.17", positionList);
        createDateSignalFrequency(strategyId, 1, 2, value);
        // вызываем метод getStrategy
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //смотрим характеристики и находим среди них "signal-frequency"
        List<StrategyCharacteristic> characteristic = getStrategyCharacteristic(getStrategy, "trade", "signal-frequency");
        //проверяем полученные данные с расчетами
        checkCharacteristics(characteristic, "signal-frequency", "Частота сделок", result);
    }


    private static Stream<Arguments> provideMasterPortfolioPositionRetention() {
        return Stream.of(
            Arguments.of("days", "до дня"),
            Arguments.of("weeks", "до недели"),
            Arguments.of("months", "до месяца"),
            Arguments.of("forever", "больше месяца")
        );
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideMasterPortfolioPositionRetention")
    @AllureId("1266934")
    @DisplayName("C1266934.GetStrategy.Определяем характеристики стратегии: master-portfolio-position-retention")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1266934(String value, String result) throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2, LocalDateTime.now(), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(5).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = createListMasterPosition(date, 5,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        createMasterPortfolio(contractIdMaster, strategyId, 6, "6259.17", positionList);
        createDateMasterPortfolioPositionRetention(strategyId, 1, 2, value);
        // вызываем метод getStrategy
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //смотрим характеристики и находим среди них "master-portfolio-position-retention"
        List<StrategyCharacteristic> characteristic = getStrategyCharacteristic(getStrategy, "trade", "master-portfolio-position-retention");
        //проверяем полученные данные с расчетами
        checkCharacteristics(characteristic, "master-portfolio-position-retention", "Ср. время удержания позиции", result);
    }


    @SneakyThrows
    @Test
    @AllureId("1267043")
    @DisplayName("C1267043.GetStrategy.Определяем характеристики стратегии: portfolioRate")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1267043() throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2, LocalDateTime.now(), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(5).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = createListMasterPosition(date, 5,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        createMasterPortfolio(contractIdMaster, strategyId, 6, "6259.17", positionList);
        createDateMasterPortfolioRate(strategyId, 1, 2);
        // вызываем метод getStrategy
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        List<PortfolioRateSection> portfolioRateTypes = getStrategy.getPortfolioRate().getTypes();
        assertThat("% Акции не равно", portfolioRateTypes.get(0).getPercent().toString(), is("87.83"));
        assertThat("% Валюта не равно", portfolioRateTypes.get(1).getPercent().toString(), is("10.82"));
        assertThat("% ETF не равно", portfolioRateTypes.get(2).getPercent().toString(), is("0.93"));
        assertThat("% Облигации не равно", portfolioRateTypes.get(3).getPercent().toString(), is("0.42"));
        List<PortfolioRateSection> portfolioRateSectors = getStrategy.getPortfolioRate().getSectors();
        assertThat("% Энергетика не равно", portfolioRateSectors.get(0).getPercent().toString(), is("86.03"));
        assertThat("% Денежные средства не равно", portfolioRateSectors.get(1).getPercent().toString(), is("10.82"));
        assertThat("% Финансовый сектор не равно", portfolioRateSectors.get(2).getPercent().toString(), is("1.85"));
        assertThat("% Другое средства не равно", portfolioRateSectors.get(3).getPercent().toString(), is("0.90"));
        assertThat("% Государственные бумаги не равно", portfolioRateSectors.get(4).getPercent().toString(), is("0.40"));
        List<PortfolioRateSection> portfolioRateCompanies = getStrategy.getPortfolioRate().getCompanies();
        assertThat("% Транснефть не равно", portfolioRateCompanies.get(0).getPercent().toString(), is("79.48"));
        assertThat("% Денежные средства не равно", portfolioRateCompanies.get(1).getPercent().toString(), is("10.82"));
        assertThat("% Лукойл не равно", portfolioRateCompanies.get(2).getPercent().toString(), is("5.98"));
        assertThat("% Сбер Банк не равно", portfolioRateCompanies.get(3).getPercent().toString(), is("1.85"));
        assertThat("% РСХБ Управление Активами не равно", portfolioRateCompanies.get(4).getPercent().toString(), is("0.93"));
        assertThat("% Сургутнефтегаз не равно", portfolioRateCompanies.get(5).getPercent().toString(), is("0.57"));
        assertThat("% ОФЗ не равно", portfolioRateCompanies.get(6).getPercent().toString(), is("0.40"));
    }


    @SneakyThrows
    @Test
    @AllureId("1267127")
    @DisplayName("C1267127.GetStrategy.Определяем характеристики стратегии: topPositions")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1267127() throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2, LocalDateTime.now(), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(5).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = createListMasterPosition(date, 5,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        createMasterPortfolio(contractIdMaster, strategyId, 6, "6259.17", positionList);
        createDateMasterPortfolioTopPositions(strategyId, 1, 2);
        // вызываем метод getStrategy
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        List<ru.qa.tinkoff.swagger.tracking.model.TopPosition> topPositions = getStrategy.getTopPositions();
        for (int i = 0; i < topPositions.size(); i++) {
            if (topPositions.get(i).getExchangePosition().getTicker().equals(instrument.tickerSBER)) {
                assertThat("briefName не равно", topPositions.get(i).getExchangePosition().getBriefName(), is(instrument.briefNameSBER));
                assertThat("image не равно", topPositions.get(i).getExchangePosition().getImage(), is(instrument.imageSBER));
                assertThat("type не равно", topPositions.get(i).getExchangePosition().getType().getValue(), is(instrument.typeSBER));
            }
            if (topPositions.get(i).getExchangePosition().getTicker().equals(instrument.tickerSU29009RMFS6)) {
                assertThat("briefName не равно", topPositions.get(i).getExchangePosition().getBriefName(), is(instrument.briefNameSU29009RMFS6));
                assertThat("image не равно", topPositions.get(i).getExchangePosition().getImage(), is(instrument.imageSU29009RMFS6));
                assertThat("type не равно", topPositions.get(i).getExchangePosition().getType().getValue(), is(instrument.typeSU29009RMFS6));
            }
        }
    }


    @SneakyThrows
    @Test
    @AllureId("1267723")
    @DisplayName("C1267723.GetStrategy.Определяем характеристики стратегии: owner")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1267723() throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2, LocalDateTime.now(), 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(5).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = createListMasterPosition(date, 5,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        createMasterPortfolio(contractIdMaster, strategyId, 6, "6259.17", positionList);
        // вызываем метод getStrategy
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        clientMaster = clientService.getClient(investIdMaster);
        assertThat("profileId не равно", getStrategy.getOwner().getSocialProfile().getId().toString(), is(clientMaster.getSocialProfile().getId().toString()));
    }

    @SneakyThrows
    @Test
    @AllureId("1261187")
    @DisplayName("C1261187.GetStrategy.Определяем характеристики стратегии: activation-interval")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1261187_1() throws JsonProcessingException, InterruptedException {
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.3", "0.05", false, null, "TEST", "TEST11");
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = createListMasterPosition(date, 5,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        createMasterPortfolio(contractIdMaster, strategyId, 6, "6259.17", positionList);
        // вызываем метод getStrategy
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));

        List<GetStrategyResponseCharacteristics> strategyCharacteristics =
            getStrategy.getCharacteristics().stream()
                .filter(strategyCharacteristic -> strategyCharacteristic.getType().getValue().equals("main"))
                .collect(Collectors.toList());
        List<StrategyCharacteristic> tailCharacteristics = new ArrayList<>();
        for (int i = 0; i < strategyCharacteristics.get(0).getItems().size(); i++) {
            if (strategyCharacteristics.get(0).getItems().get(i).getId().equals("activation-interval")) {
                tailCharacteristics.add(strategyCharacteristics.get(0).getItems().get(i));
            }
        }
        //проверяем полученные данные с расчетами
        assertThat("id характеристики не равно", tailCharacteristics.size(), is(0));


    }


    private static Stream<Arguments> validateExpectedRelativeYield() {
        return Stream.of(
            Arguments.of(BigDecimal.valueOf(-56.01)),
            Arguments.of(BigDecimal.valueOf(0)),
            Arguments.of(BigDecimal.valueOf(1)),
            Arguments.of(BigDecimal.valueOf(99)),
            Arguments.of(BigDecimal.valueOf(240))
        );
    }

    @ParameterizedTest
    @MethodSource("validateExpectedRelativeYield")
    @AllureId("1583315")
    @DisplayName("С1583315.GetStrategy.Получение expected-relative-yield")
    @Subfeature("Успешные сценарии")
    @Description("Метод возвращает информацию по торговой стратегии: основные показатели, доли виртуального портфеля, торговые показатели.")
    void C1583315(BigDecimal expectedRelativeYield) {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.3", "0.05", false, expectedRelativeYield, "TEST", "TEST11");
        //создаем запись в кассандре
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        createMasterPortfolio(contractIdMaster, strategyId, 1, "6259.17", positionList);
        GetStrategyResponse getStrategy = strategyApiCreator.get().getStrategy()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategyResponse.class));
        //Находим в БД автоследования стратегию и Проверяем ее поля
        strategyMaster = strategyService.getStrategy(strategyId);
        List<GetStrategyResponseCharacteristics> strategyCharacteristics =
            getStrategy.getCharacteristics().stream()
                .filter(strategyCharacteristic -> strategyCharacteristic.getType().getValue().equals("main"))
                .collect(Collectors.toList());

        //Проверяем, что нашли expected-relative-yield
        assertThat("id не равно", strategyCharacteristics.get(0).getItems().get(0).getId(), is(expectedRelativeYieldName));
        assertThat("value не равно", strategyCharacteristics.get(0).getItems().get(0).getTitle(), is("Прогноз автора"));
        assertThat("title не равно", strategyCharacteristics.get(0).getItems().get(0).getValue(), is(expectedRelativeYield.toString() + "% в год"));

    }


    BigDecimal roundDecimal(BigDecimal recommendedBaseMoneyPositionQuantity, int multiplicity) {
        BigInteger integer = recommendedBaseMoneyPositionQuantity.setScale(0, RoundingMode.UP).toBigInteger();
        BigInteger mod = integer.mod(BigInteger.valueOf(multiplicity));
        if (mod.compareTo(BigInteger.ZERO) == 0) {
            return new BigDecimal(integer);
        }
        return new BigDecimal(
            integer.add(BigInteger.valueOf(multiplicity)).subtract(mod));
    }


    public void createMasterPortfolio(String contractIdMaster, UUID strategyId, int version,
                                      String money, List<MasterPortfolio.Position> positionList) {
        //создаем портфель master в cassandra
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //с базовой валютой
        MasterPortfolio.BaseMoneyPosition baseMoneyPosition = MasterPortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        masterPortfolioDao.insertIntoMasterPortfolioWithChangedAt(contractIdMaster, strategyId, version, baseMoneyPosition, positionList, date);
    }

    //позиции в портфеле мастера с разными типами инструментов
    public List<MasterPortfolio.Position> createListMasterPosition(Date date, int lastChangeDetectedVersion, Tracking.Portfolio.Position position) {
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .quantity(new BigDecimal(quantitySBER))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerFXDE)
            .tradingClearingAccount(instrument.tradingClearingAccountFXDE)
            .quantity(new BigDecimal(quantityFXDE))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .quantity(new BigDecimal(quantitySU29009RMFS6))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(instrument.tickerUSD)
            .tradingClearingAccount(instrument.tradingClearingAccountUSD)
            .quantity(new BigDecimal(quantityUSD))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        return positionList;
    }


    void createDateMasterPortfolioValue(UUID strategyId, int days, int hours, String value) {
        masterPortfolioValue = MasterPortfolioValue.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .value(new BigDecimal(value))
            .build();
        masterPortfolioValueDao.insertIntoMasterPortfolioValue(masterPortfolioValue);
    }


    void createDateMasterPortfolioMaxDrawdown(UUID strategyId, int days, int hours, String value) {
        masterPortfolioMaxDrawdown = MasterPortfolioMaxDrawdown.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .value(new BigDecimal(value))
            .build();
        masterPortfolioMaxDrawdownDao.insertIntoMasterPortfolioMaxDrawdown(masterPortfolioMaxDrawdown);
    }

    void createDateSignalFrequency(UUID strategyId, int days, int hours, int count) {
        signalFrequency = SignalFrequency.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .count(count)
            .build();
        signalFrequencyDao.insertIntoSignalFrequency(signalFrequency);
    }


    void createDateSignalsCount(UUID strategyId, int days, int hours, int value) {
        signalsCount = SignalsCount.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .value(value)
            .build();
        signalsCountDao.insertIntoSignalsCount(signalsCount);
    }


    void createDateStrategyTailValue(UUID strategyId, int days, int hours, String value) {
        strategyTailValue = StrategyTailValue.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .value(new BigDecimal(value))
            .build();
        strategyTailValueDao.insertIntoStrategyTailValue(strategyTailValue);
    }


    void createDateMasterPortfolioPositionRetention(UUID strategyId, int days, int hours, String value) {
        masterPortfolioPositionRetention = MasterPortfolioPositionRetention.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .value(value)
            .build();
        masterPortfolioPositionRetentionDao.insertIntoMasterPortfolioPositionRetention(masterPortfolioPositionRetention);
    }


    void createDateMasterPortfolioRate(UUID strategyId, int days, int hours) {
        Map<String, BigDecimal> companyToRateMap = new HashMap<>();
        companyToRateMap.put("Денежные средства", new BigDecimal("0.1082"));
        companyToRateMap.put("Лукойл", new BigDecimal("0.0598"));
        companyToRateMap.put("ОФЗ", new BigDecimal("0.004"));
        companyToRateMap.put("РСХБ Управление Активами", new BigDecimal("0.0093"));
        companyToRateMap.put("Сбер Банк", new BigDecimal("0.0185"));
        companyToRateMap.put("Сургутнефтегаз", new BigDecimal("0.0057"));
        companyToRateMap.put("Транснефть", new BigDecimal("0.7948"));

        Map<String, BigDecimal> sectorToRateMap = new HashMap<>();
        sectorToRateMap.put("energy", new BigDecimal("0.8603"));
        sectorToRateMap.put("financial", new BigDecimal("0.0185"));
        sectorToRateMap.put("government", new BigDecimal("0.004"));
        sectorToRateMap.put("money", new BigDecimal("0.1082"));
        sectorToRateMap.put("other", new BigDecimal("0.009"));

        Map<String, BigDecimal> typeToRateMap = new HashMap<>();
        typeToRateMap.put("bond", new BigDecimal("0.0042"));
        typeToRateMap.put("etf", new BigDecimal("0.0093"));
        typeToRateMap.put("money", new BigDecimal("0.1082"));
        typeToRateMap.put("share", new BigDecimal("0.8783"));

        masterPortfolioRate = MasterPortfolioRate.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .companyToRateMap(companyToRateMap)
            .sectorToRateMap(sectorToRateMap)
            .typeToRateMap(typeToRateMap)
            .build();
        masterPortfolioRateDao.insertIntoMasterPortfolioRate(masterPortfolioRate);
    }


    void createDateMasterPortfolioTopPositions(UUID strategyId, int days, int hours) {
        List<MasterPortfolioTopPositions.TopPositions> topPositions = new ArrayList<>();
        topPositions.add(MasterPortfolioTopPositions.TopPositions.builder()
            .ticker(instrument.tickerSBER)
            .tradingClearingAccount(instrument.tradingClearingAccountSBER)
            .signalsCount(3)
            .build());
        topPositions.add(MasterPortfolioTopPositions.TopPositions.builder()
            .ticker(instrument.tickerSU29009RMFS6)
            .tradingClearingAccount(instrument.tradingClearingAccountSU29009RMFS6)
            .signalsCount(7)
            .build());
        masterPortfolioTopPositions = MasterPortfolioTopPositions.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .positions(topPositions)
            .build();
        masterPortfolioTopPositionsDao.insertIntoMasterPortfolioTopPositions(masterPortfolioTopPositions);

    }

    void createMasterSignal(int minusDays, int minusHours, int version, UUID strategyId, String ticker, String tradingClearingAccount,
                            String price, String quantity, int action) {
        LocalDateTime time = LocalDateTime.now().minusDays(minusDays).minusHours(minusHours);
        Date convertedDatetime = Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
        MasterSignal masterSignal = MasterSignal.builder()
            .strategyId(strategyId)
            .version(version)
            .ticker(ticker)
            .tradingClearingAccount(tradingClearingAccount)
            .action((byte) action)
            .state((byte) 1)
            .price(new BigDecimal(price))
            .quantity(new BigDecimal(quantity))
            .createdAt(convertedDatetime)
            .build();
        masterSignalDao.insertIntoMasterSignal(masterSignal);
    }

    void createTestDateToMasterSignal(UUID strategyId) {
        createMasterSignal(4, 3, 2, strategyId, instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            "289.37", "10", 12);
        createMasterSignal(4, 2, 3, strategyId, instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            "289.37", "10", 12);
        createMasterSignal(4, 1, 4, strategyId, instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            "289.37", "10", 12);
        createMasterSignal(3, 7, 5, strategyId, instrument.tickerFXDE, instrument.tradingClearingAccountFXDE,
            "3310", "5", 12);
        createMasterSignal(3, 1, 6, strategyId, instrument.tickerSU29009RMFS6, instrument.tradingClearingAccountSU29009RMFS6,
            "106.663", "7", 12);
        createMasterSignal(2, 2, 7, strategyId, instrument.tickerUSD, instrument.tradingClearingAccountUSD,
            "70.8425", "2000", 12);
    }


    List<StrategyCharacteristic> getStrategyCharacteristic(GetStrategyResponse getStrategy, String typeChar, String itemChar) {
        List<GetStrategyResponseCharacteristics> strategyCharacteristics =
            getStrategy.getCharacteristics().stream()
                .filter(strategyCharacteristic -> strategyCharacteristic.getType().getValue().equals(typeChar))
                .collect(Collectors.toList());
        List<StrategyCharacteristic> characteristics = new ArrayList<>();
        for (int i = 0; i < strategyCharacteristics.get(0).getItems().size(); i++) {
            if (strategyCharacteristics.get(0).getItems().get(i).getId().equals(itemChar)) {
                characteristics.add(strategyCharacteristics.get(0).getItems().get(i));
            }
        }
        return characteristics;

    }

    void checkCharacteristics(List<StrategyCharacteristic> characteristics, String id, String title, String value) {
        //проверяем полученные данные
        assertThat("id характеристики не равно", characteristics.get(0).getId(), is(id));
        assertThat("title не равно", characteristics.get(0).getTitle(), is(title));
        assertThat("value не равно", characteristics.get(0).getValue(), is(value));
    }

}
