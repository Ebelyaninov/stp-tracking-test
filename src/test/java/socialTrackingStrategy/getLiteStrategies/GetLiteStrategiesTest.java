package socialTrackingStrategy.getLiteStrategies;


import com.fasterxml.jackson.core.JsonProcessingException;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioValue;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.model.*;
import ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.model.Currency;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("getLiteStrategies - Получение облегченных данных списка стратегий")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("social-tracking-strategy")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class
})
public class GetLiteStrategiesTest {
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
    Client client;
    Contract contract;
    Strategy strategy;
    String contractIdMaster;
    UUID strategyId;
    MasterPortfolioValue masterPortfolioValue;
    StrategyApi strategyApi = ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.invoker
        .ApiClient.api(ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.invoker
            .ApiClient.Config.apiConfig()).strategy();

    String siebelIdMaster = "1-7XOAYPX";

    String xApiKey = "x-api-key";
    String key = "stp-tracking";
    String tickerShare = "SBER";
    String tradingClearingAccountShare = "L01+00002F00";
    String quantityShare = "30";

    String tickerEtf = "FXDE";
    String tradingClearingAccountEtf = "L01+00002F00";
    String quantityEtf = "5";

    String tickerBond = "SU29009RMFS6";
    String tradingClearingAccountBond = "L01+00002F00";
    String quantityBond = "7";

    String tickerMoney = "USD000UTSTOM";
    String tradingClearingAccountMoney = "MB9885503216";
    String quantityMoney = "2000";

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {

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
        });
    }


    @Test
    @AllureId("1135430")
    @DisplayName("C1135430.GetLiteStrategies.Получение облегченных данных списка стратегий.Данные по стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1135430() throws JsonProcessingException, InterruptedException {
        //вызываем метод getLiteStrategy
        GetLiteStrategiesResponse getLiteStrategies = strategyApi.getLiteStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategiesResponse.class));
        List<Strategy> strategysFromDB = contractService.getStrategyByStatusWithProfile(StrategyStatus.active);
        //записываем stratedyId в множества и сравниваем их
        Set<UUID> listStrategyIdsFromApi = new HashSet<>();
        Set<String>  listStrategyTitleFromApi = new HashSet<>();
        Set<String> listStrategyBaseCurrencyFromApi = new HashSet<>();
        Set<String> listStrategyRiskProfileFromApi = new HashSet<>();
        Set<Integer> listStrategyScoreFromApi = new HashSet<>();
        for (int i = 0; i < getLiteStrategies.getItems().size(); i++) {
            listStrategyIdsFromApi.add(getLiteStrategies.getItems().get(i).getId());
            listStrategyTitleFromApi.add(getLiteStrategies.getItems().get(i).getTitle());
            listStrategyBaseCurrencyFromApi.add(getLiteStrategies.getItems().get(i).getBaseCurrency().toString());
            listStrategyRiskProfileFromApi.add(getLiteStrategies.getItems().get(i).getRiskProfile().toString());
            listStrategyScoreFromApi.add(getLiteStrategies.getItems().get(i).getScore());
        }
        Set<String>  listStrategyTitleFromDB = new HashSet<>();
        Set<UUID> listStrategyIdsFromDB = new HashSet<>();
        Set<String> listStrategyBaseCurrencyFromDB = new HashSet<>();
        Set<String> listStrategyRiskProfileFromDB = new HashSet<>();
        Set<Integer> listStrategyScoreFromDB = new HashSet<>();

        for (int i = 0; i < strategysFromDB.size(); i++) {
            listStrategyIdsFromDB.add(strategysFromDB.get(i).getId());
            listStrategyTitleFromDB.add(strategysFromDB.get(i).getTitle());
            listStrategyBaseCurrencyFromDB.add(strategysFromDB.get(i).getBaseCurrency().toString());
            listStrategyRiskProfileFromDB.add(strategysFromDB.get(i).getRiskProfile().toString());
            listStrategyScoreFromDB.add(strategysFromDB.get(i).getScore());
        }
        assertThat("идентификаторы стратегий не совпадают", listStrategyIdsFromApi, is(listStrategyIdsFromDB));
        assertThat("title стратегий не совпадают", listStrategyTitleFromApi, is(listStrategyTitleFromDB));
        assertThat("baseCurrency стратегий не совпадают", listStrategyBaseCurrencyFromApi, is(listStrategyBaseCurrencyFromDB));
        assertThat("riskProfile стратегий не совпадают", listStrategyRiskProfileFromApi, is(listStrategyRiskProfileFromDB));
        assertThat("score стратегий не совпадают", listStrategyScoreFromApi, is(listStrategyScoreFromDB));
    }


    @Test
    @AllureId("1140305")
    @DisplayName("C1140305.GetLiteStrategies.Данные о владельце стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1140305()  {
        //вызываем метод getLiteStrategy
        GetLiteStrategiesResponse getLiteStrategies = strategyApi.getLiteStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategiesResponse.class));
        List<Strategy> strategysFromDB = strategyService.getStrategyByStatus(StrategyStatus.active);
        UUID strId = strategysFromDB.get(0).getId();
        String contractId = strategysFromDB.get(0).getContract().getId();
        contract = contractService.getContract(contractId);
        client = clientService.getClient(contract.getClientId());

        List<LiteStrategy> liteStrategy = new ArrayList<>();
        for (int i = 0; i < getLiteStrategies.getItems().size(); i++) {
            if (getLiteStrategies.getItems().get(i).getId().toString().equals(strId.toString())) {
                liteStrategy.add(getLiteStrategies.getItems().get(i));
            }
        }
        assertThat("socialProfile owner стратегии не совпадают", liteStrategy.get(0).getOwner().getSocialProfile().getId().toString(), is(client.getSocialProfile().getId()));
        assertThat("nickname owner стратегии не совпадают", liteStrategy.get(0).getOwner().getSocialProfile().getNickname(), is(client.getSocialProfile().getNickname()));
        assertThat("image owner стратегии не совпадают", liteStrategy.get(0).getOwner().getSocialProfile().getImage().toString(), is(client.getSocialProfile().getImage().toString()));
    }


    @Test
    @AllureId("1140313")
    @DisplayName("C1140313.GetLiteStrategies.Данные по характеристикам, доходности и точкам со стоимостью портфеля")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1140313() throws JsonProcessingException, InterruptedException {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest " +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategyFee(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2000, LocalDateTime.now(), "0.3", "0.05");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        final int daysAgo = 366;
        LocalDateTime from = LocalDateTime.now().minusDays(daysAgo);
        strategy.setActivationTime(from);
        strategyService.saveStrategy(strategy);
        //добавляем записи о стоимости портфеля за разные периода
        createPortfolioValuesDate(from, date);
        //считаем точку
        BigDecimal normalizedMinMaxDif = new BigDecimal("99").subtract(new BigDecimal("0"));
        BigDecimal minMaxDif = new BigDecimal("109268.75").subtract(new BigDecimal("68349.11"));
        BigDecimal point = new BigDecimal("96268.75")
            .subtract(new BigDecimal("68349.11"))
            .divide(minMaxDif, 4, RoundingMode.HALF_UP)
            .multiply(normalizedMinMaxDif)
            .add(new BigDecimal("0"));
        List<Double> portfolioValuesPoints = new ArrayList<>();
        portfolioValuesPoints.add(0.0);
        portfolioValuesPoints.add(point.setScale(0, RoundingMode.HALF_UP).doubleValue());
        portfolioValuesPoints.add(99.0);
        //считаем recommended-base-money-position-quantity
        BigDecimal recommendedBaseMoneyPositionQuantity = new BigDecimal("109268.75")
            .add(new BigDecimal("109268.75").multiply(new BigDecimal("0.05")));
        recommendedBaseMoneyPositionQuantity = roundDecimal(recommendedBaseMoneyPositionQuantity);
        String str = String.format("%,d", recommendedBaseMoneyPositionQuantity.intValue());
        String rubbleSymbol = "₽";
        String recommendedBaseMoney = str.replace(",", " ") + " " + rubbleSymbol;
        //рассчитываем относительную доходность но основе выбранных точек Values
        BigDecimal relativeYield = (new BigDecimal("109268.75")
            .divide(new BigDecimal("68349.11"), 4, RoundingMode.HALF_UP))
            .subtract(new BigDecimal("1"))
            .multiply(new BigDecimal("100"))
            .setScale(2, BigDecimal.ROUND_HALF_EVEN);
        //вызываем метод getLiteStrategy
        for (int i = 0; i < 7; i++) {
            GetLiteStrategiesResponse getLiteStrategies = strategyApi.getLiteStrategies()
                .reqSpec(r -> r.addHeader(xApiKey, key))
                .xAppNameHeader("stp-tracking-api")
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(GetLiteStrategiesResponse.class));
            List<LiteStrategy>  LiteStrategy = getLiteStrategies.getItems().stream()
                    .filter(ls -> ls.getId().toString().equals(strategyId.toString()))
                    .collect(Collectors.toList());

                if (LiteStrategy.size() == 1) {
                    break;
                }
            Thread.sleep(20000);
        }

        GetLiteStrategiesResponse getLiteStrategies = strategyApi.getLiteStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategiesResponse.class));
        List<LiteStrategy> liteStrategy = new ArrayList<>();
        for (int i = 0; i < getLiteStrategies.getItems().size(); i++) {
            if (getLiteStrategies.getItems().get(i).getId().toString().equals(strategyId.toString())) {
                liteStrategy.add(getLiteStrategies.getItems().get(i));
            }
        }
        // выбираем характеристику по recommended-base-money-position-quantity
        List<StrategyCharacteristic> strategyCharacteristicsBaseMoney =
            liteStrategy.get(0).getCharacteristics().stream()
                .filter(strategyCharacteristic -> strategyCharacteristic.getId().equals("recommended-base-money-position-quantity"))
                .collect(Collectors.toList());
        // выбираем характеристику по slaves-count
        List<StrategyCharacteristic> strategyCharacteristicsSlavesCount =
            liteStrategy.get(0).getCharacteristics().stream()
                .filter(strategyCharacteristic -> strategyCharacteristic.getId().equals("slaves-count"))
                .collect(Collectors.toList());
        assertThat("идентификатор стратегии не равно", liteStrategy.get(0).getId(), is(strategyId));
        assertThat("value recommended-base-money-position-quantity не равно", strategyCharacteristicsBaseMoney.get(0).getValue(),
            is(recommendedBaseMoney));
        assertThat("subtitle recommended-base-money-position-quantity не равно", strategyCharacteristicsBaseMoney.get(0).getSubtitle(),
            is("советуем вложить"));
        assertThat("value slaves-count не равно", strategyCharacteristicsSlavesCount.get(0).getValue(),
            is("2" + "\u00A0" + "000"));
        assertThat("subtitle slaves-count не равно", strategyCharacteristicsSlavesCount.get(0).getSubtitle(),
            is("подписаны"));
        assertThat("portfolioValues стратегии не равно", liteStrategy.get(0).getPortfolioValues(), is(portfolioValuesPoints));
        assertThat("relativeYield стратегии не равно", liteStrategy.get(0).getRelativeYield(), is(relativeYield.doubleValue()));
    }


    @Test
    @AllureId("1135431")
    @DisplayName("C1135431.GetLiteStrategy.Валидация входного запроса: x-app-name")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1135431() throws JSONException {
        //вызываем метод getLiteStrategy
        StrategyApi.GetLiteStrategiesOper getLiteStrategies = strategyApi.getLiteStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, key))
//            .xAppNameHeader("stp-tracking-api")
            .respSpec(spec -> spec.expectStatusCode(400));
        getLiteStrategies.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getLiteStrategies.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("0000-00-Z99"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @Test
    @AllureId("1140346")
    @DisplayName("C1140346.GetLiteStrategy.Валидация входного запроса: X-API-KEY")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1140346() throws JSONException {
        //вызываем метод getLiteStrategy
        StrategyApi.GetLiteStrategiesOper getLiteStrategies = strategyApi.getLiteStrategies()
//            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .respSpec(spec -> spec.expectStatusCode(401));
        getLiteStrategies.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getLiteStrategies.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("0000-00-Z99"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно прав"));
    }


    @Test
    @AllureId("1140349")
    @DisplayName("C1140349.GetLiteStrategies.Стратегия в статусе draft")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1140349() throws JsonProcessingException, InterruptedException {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategy(siebelIdMaster, investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null);
        Thread.sleep(15000);
        //вызываем метод getLiteStrategy
        GetLiteStrategiesResponse getLiteStrategies = strategyApi.getLiteStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategiesResponse.class));
        List<LiteStrategy> liteStrategy = new ArrayList<>();
        for (int i = 0; i < getLiteStrategies.getItems().size(); i++) {
            if (getLiteStrategies.getItems().get(i).getId().toString().equals(strategyId.toString())) {
                liteStrategy.add(getLiteStrategies.getItems().get(i));
            }
        }
        assertThat("идентификатор стратегии не равно", liteStrategy.size(), is(0));
    }


    @Test
    @AllureId("1140352")
    @DisplayName("C1140352.GetLiteStrategies.Стратегия без данных по Socialprofile")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1140352() throws JsonProcessingException, InterruptedException {
        int randomNumber = 0 + (int) (Math.random() * 100);
        String title = "Autotest" +String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategyWithOutProfile(investIdMaster, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2000, LocalDateTime.now(), "0.3", "0.05");
        Thread.sleep(15000);
        //вызываем метод getLiteStrategy
        GetLiteStrategiesResponse getLiteStrategies = strategyApi.getLiteStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategiesResponse.class));
        List<LiteStrategy> liteStrategy = new ArrayList<>();
        for (int i = 0; i < getLiteStrategies.getItems().size(); i++) {
            if (getLiteStrategies.getItems().get(i).getId().toString().equals(strategyId.toString())) {
                liteStrategy.add(getLiteStrategies.getItems().get(i));
            }
        }
        assertThat("идентификатор стратегии не равно", liteStrategy.size(), is(0));
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
            .ticker(tickerShare)
            .tradingClearingAccount(tradingClearingAccountShare)
            .quantity(new BigDecimal(quantityShare))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerEtf)
            .tradingClearingAccount(tradingClearingAccountEtf)
            .quantity(new BigDecimal(quantityEtf))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerBond)
            .tradingClearingAccount(tradingClearingAccountBond)
            .quantity(new BigDecimal(quantityBond))
            .changedAt(date)
            .lastChangeDetectedVersion(lastChangeDetectedVersion)
            .lastChangeAction((byte) position.getAction().getActionValue())
            .build());
        positionList.add(MasterPortfolio.Position.builder()
            .ticker(tickerMoney)
            .tradingClearingAccount(tradingClearingAccountMoney)
            .quantity(new BigDecimal(quantityMoney))
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
        createMasterSignal(4, 3, 2, strategyId, tickerShare, tradingClearingAccountShare,
            "289.37", "10", 12);
        createMasterSignal(4, 2, 3, strategyId, tickerShare, tradingClearingAccountShare,
            "289.37", "10", 12);
        createMasterSignal(4, 1, 4, strategyId, tickerShare, tradingClearingAccountShare,
            "289.37", "10", 12);
        createMasterSignal(3, 7, 5, strategyId, tickerEtf, tradingClearingAccountEtf,
            "3310", "5", 12);
        createMasterSignal(3, 1, 6, strategyId, tickerBond, tradingClearingAccountBond,
            "106.663", "7", 12);
        createMasterSignal(2, 2, 7, strategyId, tickerMoney, tradingClearingAccountMoney,
            "70.8425", "2000", 12);
    }

    BigDecimal roundDecimal(BigDecimal recommendedBaseMoneyPositionQuantity) {
        BigInteger integer = recommendedBaseMoneyPositionQuantity.setScale(0, RoundingMode.UP).toBigInteger();
        BigInteger mod = integer.mod(BigInteger.valueOf(5000));
        if (mod.compareTo(BigInteger.ZERO) == 0) {
            return new BigDecimal(integer);
        }
        return new BigDecimal(
            integer.add(BigInteger.valueOf(5000)).subtract(mod));
    }

    void createPortfolioValuesDate(LocalDateTime from, Date date) {
        long diffMs = Duration.between(from, LocalDateTime.now()).getSeconds() * 1000;
        BigDecimal interval = BigDecimal.valueOf(diffMs).divide(BigDecimal.valueOf(100 - 1), RoundingMode.HALF_UP);
        LocalDateTime middleStart = from.plusSeconds(interval.longValue() * 50 / 1000).toLocalDate().atStartOfDay();
        long daysMiddleDiff = Duration.between(middleStart, LocalDateTime.now()).getSeconds() / 86400;
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = createListMasterPosition(date, 5,
            steps.createPosAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE));
        createMasterPortfolio(contractIdMaster, strategyId, 6, "6259.17", positionList);
        //создаем запись о стоимости портфеля
        createDateMasterPortfolioValue(strategyId, 365, 3, "68349.11");
        createDateMasterPortfolioValue(strategyId, (int) daysMiddleDiff, 4, "96268.75");
        createDateMasterPortfolioValue(strategyId, (int) daysMiddleDiff + 1, 4, "98268.75");
        createDateMasterPortfolioValue(strategyId, 2, 4, "108268.75");
        createDateMasterPortfolioValue(strategyId, 1, 4, "109268.75");

    }
}
