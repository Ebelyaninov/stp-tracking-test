package socialTrackingStrategy.getLiteStrategies;


import com.fasterxml.jackson.core.JsonProcessingException;
import extenstions.RestAssuredExtension;
import io.qameta.allure.Allure;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioTopPositions;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioValue;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.SocialProfile;
import ru.qa.tinkoff.social.entities.TestsStrategy;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.model.GetLiteStrategiesResponse;
import ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.model.LiteStrategy;
import ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.model.StrategyCharacteristic;
import ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.model.StrategyTag;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.trading.tracking.Tracking;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

@Slf4j
@Epic("getLiteStrategies - Получение облегченных данных списка стратегий")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("social-tracking-strategy")
@Tags({@Tag("social-tracking-strategy"), @Tag("getLiteStrategies")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    ApiCreatorConfiguration.class,
    StpTrackingSiebelConfiguration.class
})
public class GetLiteStrategiesTest {
    @Autowired
    StrategyService strategyService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StpTrackingApiSteps steps;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    MasterPortfolioValueDao masterPortfolioValueDao;
    @Autowired
    MasterPortfolioTopPositionsDao masterPortfolioTopPositionsDao;
    @Autowired
    MasterSignalDao masterSignalDao;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel stpSiebel;

    Client client;
    Contract contract;
    Strategy strategy;
    String contractIdMaster;
    MasterPortfolioTopPositions masterPortfolioTopPositions;
    UUID strategyId;
    MasterPortfolioValue masterPortfolioValue;
    StrategyApi strategyApi = ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.invoker
        .ApiClient.api(ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.invoker
            .ApiClient.Config.apiConfig()).strategy();
    String siebelIdMaster;
    String xApiKey = "x-api-key";
    String key = "stp-tracking";
    BigDecimal expectedRelativeYield = new BigDecimal(58.00);
    String quantitySBER = "30";
    String quantityFXDE = "5";
    String quantitySU29009RMFS6 = "7";
    String quantityUSD = "2000";
    BigDecimal masterValueAdditionalRate = new BigDecimal("0.05");

    @BeforeAll
    void createTestData(){
        siebelIdMaster = stpSiebel.siebelIdMasterSocialTrackingStrategy;
        steps.deleteDataFromDb(siebelIdMaster);
    }

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
            try {
                masterPortfolioTopPositionsDao.deleteMasterPortfolioTopPositionsByStrategyId(strategyId);
            } catch (Exception e) {
            }
        });
    }


    @Test
    @AllureId("1135430")
    @DisplayName("C1135430.GetLiteStrategies.Получение облегченных данных списка стратегий.Данные по стратегии")
    @Subfeature("Успешные сценарии")
    @SneakyThrows
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1135430() throws JsonProcessingException, InterruptedException {
        //вызываем метод getLiteStrategy
        GetLiteStrategiesResponse getLiteStrategies = strategyApi.getLiteStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategiesResponse.class));
        List<Strategy> strategysFromDB = contractService.getStrategyByTwoStatusWithProfile(StrategyStatus.active, StrategyStatus.frozen);
        //записываем stratedyId в множества и сравниваем их
        Set<UUID> listStrategyIdsFromApi = new HashSet<>();
        Set<String> listStrategyTitleFromApi = new HashSet<>();
        Set<String> listStrategyBaseCurrencyFromApi = new HashSet<>();
        Set<String> listStrategyRiskProfileFromApi = new HashSet<>();
        Set<String> listStrategyStatusFromApi = new HashSet<>();
        Set<Integer> listStrategyScoreFromApi = new HashSet<>();
        Set<Boolean> listStrategyIsOverloadedFromApi = new HashSet<>();
        Set<String> listStrategyPortfolioValues = new HashSet<>();
        Set<LocalDateTime> listStrategyActivationTimeFromApi = new HashSet<>();
        Set<String> listStrategyActivationTimeFromApiString = new HashSet<>();
        Set<List<StrategyTag>> listStrategyTagsTimeFromApiString = new HashSet<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
        for (int i = 0; i < getLiteStrategies.getItems().size(); i++) {
            listStrategyIdsFromApi.add(getLiteStrategies.getItems().get(i).getId());
            listStrategyTitleFromApi.add(getLiteStrategies.getItems().get(i).getTitle());
            listStrategyBaseCurrencyFromApi.add(getLiteStrategies.getItems().get(i).getBaseCurrency().toString());
            listStrategyRiskProfileFromApi.add(getLiteStrategies.getItems().get(i).getRiskProfile().toString());
            listStrategyStatusFromApi.add(getLiteStrategies.getItems().get(i).getStatus().toString());
            listStrategyScoreFromApi.add(getLiteStrategies.getItems().get(i).getScore());
            listStrategyIsOverloadedFromApi.add(getLiteStrategies.getItems().get(i).getIsOverloaded());
            listStrategyPortfolioValues.add(getLiteStrategies.getItems().get(i).getPortfolioValues().toString());
            listStrategyActivationTimeFromApi.add(getLiteStrategies.getItems().get(i).getActivationTime().toLocalDateTime());
            listStrategyActivationTimeFromApiString.add(formatter.format(getLiteStrategies.getItems().get(i).getActivationTime().atZoneSameInstant(ZoneId.of("Europe/Moscow")).toLocalDateTime()));
            listStrategyTagsTimeFromApiString.add(getLiteStrategies.getItems().get(i).getTags());
        }
        Set<String> listStrategyTitleFromDB = new HashSet<>();
        Set<UUID> listStrategyIdsFromDB = new HashSet<>();
        Set<String> listStrategyBaseCurrencyFromDB = new HashSet<>();
        Set<String> listStrategyRiskProfileFromDB = new HashSet<>();
        Set<Integer> listStrategyScoreFromDB = new HashSet<>();
        Set<Boolean> listIsOverloaded = new HashSet<>();
        Set<String> listStrategyStatusFromDB = new HashSet<>();
        Set<LocalDateTime> listStrategyActivationTimeFromDB = new HashSet<>();
        Set<String> listStrategyActivationTimeFromDBString = new HashSet<>();
        Set<List<StrategyTag>> listStrategyTagsTimeFromDBString = new HashSet<>();

        for (int i = 0; i < strategysFromDB.size(); i++) {
            listStrategyIdsFromDB.add(strategysFromDB.get(i).getId());
            listStrategyTitleFromDB.add(strategysFromDB.get(i).getTitle());
            listStrategyBaseCurrencyFromDB.add(strategysFromDB.get(i).getBaseCurrency().toString());
            listStrategyRiskProfileFromDB.add(strategysFromDB.get(i).getRiskProfile().toString());
            listStrategyScoreFromDB.add(strategysFromDB.get(i).getScore());
            listIsOverloaded.add(strategysFromDB.get(i).getOverloaded());
            listStrategyStatusFromDB.add(strategysFromDB.get(i).getStatus().toString());
            listStrategyActivationTimeFromDB.add(strategysFromDB.get(i).getActivationTime());
            listStrategyActivationTimeFromDBString.add(formatter.format(strategysFromDB.get(i).getActivationTime()));
            listStrategyTagsTimeFromDBString.add(buildTags(strategysFromDB.get(i).getTags()));
        }
        assertAll(
            () -> assertThat("isOverloaded не совпадает", listStrategyIsOverloadedFromApi, is(listIsOverloaded)),
            () -> assertThat("идентификаторы стратегий не совпадают", listStrategyIdsFromApi, is(listStrategyIdsFromDB)),
            () -> assertThat("title стратегий не совпадают", listStrategyTitleFromApi, is(listStrategyTitleFromDB)),
            () -> assertThat("baseCurrency стратегий не совпадают", listStrategyBaseCurrencyFromApi, is(listStrategyBaseCurrencyFromDB)),
            () -> assertThat("riskProfile стратегий не совпадают", listStrategyRiskProfileFromApi, is(listStrategyRiskProfileFromDB)),
            () -> assertThat("score стратегий не совпадают", listStrategyScoreFromApi, is(listStrategyScoreFromDB)),
            () -> assertThat("status стратегий не совпадает", listStrategyStatusFromApi, is(listStrategyStatusFromDB)),
            () -> assertThat("PortfolioValues != []", listStrategyPortfolioValues.toString(), is("[[]]")),
            () -> assertThat("activationTime стратегии не равно", listStrategyActivationTimeFromApiString, is(listStrategyActivationTimeFromDBString)),
            () -> assertThat("tags стратегии не равен", listStrategyTagsTimeFromApiString, is(listStrategyTagsTimeFromDBString))
        );
        log.info("listOfCount from method == " + listStrategyIdsFromApi + "\n listOfCountFromDB == " + listStrategyIdsFromDB);
    }


    @Test
    @AllureId("1140305")
    @DisplayName("C1140305.GetLiteStrategies.Данные о владельце стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1140305() {
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
//        assertThat("image owner стратегии не совпадают", liteStrategy.get(0).getOwner().getSocialProfile().getImage().toString(), is(client.getSocialProfile().getImage().toString()));
    }


    @Test
    @AllureId("1140313")
    @DisplayName("C1140313.GetLiteStrategies.Данные по характеристикам, доходности и точкам со стоимостью портфеля")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1140313() throws JsonProcessingException, InterruptedException {
        UUID strategyId = UUID.fromString("185f3deb-40d2-4d5e-9763-53b278ea4085");
        strategy = strategyService.getStrategy(strategyId);
        String title = strategy.getTitle();
        String expectedRelativeYieldResult = strategy.getExpectedRelativeYield().toString();
        String count = strategy.getSlavesCount().toString();
        String shortDescription = strategy.getShortDescription();
        String ownerDescription = strategy.getOwnerDescription();
        //создаем запись о топе продаваемой позиции
        createDateMasterPortfolioTopPositions(strategyId, 0, 1);
        //считаем recommended-base-money-position-quantity
        //получаем последнее значение по стоимости портфеля из master_portfolio_value
        BigDecimal masterPortfolioValueLast = masterPortfolioValueDao.getMasterPortfolioValueLastByStrategyId(strategyId).getValue();
        BigDecimal recommendedBaseMoneyPositionQuantity = masterPortfolioValueLast
            .add(masterPortfolioValueLast.multiply(masterValueAdditionalRate));
        // Округляем полученное значение вверх до ближайшего целого числа,
        // кратного значению настройки currency.recommended-base-money-position-quantity-multiplicity по ключу = strategy.base_currency.
        recommendedBaseMoneyPositionQuantity = roundDecimalUSD(recommendedBaseMoneyPositionQuantity);
        String str = String.format("%,d", recommendedBaseMoneyPositionQuantity.intValue());
        String rubbleSymbol = "$";
        String recommendedBaseMoney = str.replace(",", " ") + " " + rubbleSymbol;
        //рассчитываем относительную доходность но основе выбранных точек Values
        BigDecimal masterPortfolioValueFirst = masterPortfolioValueDao.getMasterPortfolioValueFirstByStrategyId(strategyId).getValue();
        BigDecimal relativeYield = (masterPortfolioValueDao.getMasterPortfolioValueLastByStrategyId(strategyId).getValue()
            .divide(masterPortfolioValueDao.getMasterPortfolioValueFirstByStrategyId(strategyId).getValue(), 4, RoundingMode.HALF_UP))
            .subtract(new BigDecimal("1"))
            .multiply(new BigDecimal("100"))
            .setScale(2, BigDecimal.ROUND_HALF_UP);
        //в задаче TAP-16023 убрали + из expected-relative-yield
        String relativeYieldValue =  relativeYield.toString().replace(".", ",").replace("-" , "−") + "%";
        //вызываем метод getLiteStrategies
        GetLiteStrategiesResponse getLiteStrategies = strategyApi.getLiteStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategiesResponse.class));
        //отбираем из ответа стратегию для проверки
        List<LiteStrategy> liteStrategy = new ArrayList<>();
        for (int i = 0; i < getLiteStrategies.getItems().size(); i++) {
            if (getLiteStrategies.getItems().get(i).getId().toString().equals(strategyId.toString())) {
                liteStrategy.add(getLiteStrategies.getItems().get(i));
            }
        }
        // выбираем характеристику по recommended-base-money-position-quantity
        StrategyCharacteristic strategyCharacteristicsBaseMoney = getCharacteristic(liteStrategy, "recommended-base-money-position-quantity");
        // выбираем характеристику по slaves-count
        StrategyCharacteristic strategyCharacteristicsSlavesCount = getCharacteristic(liteStrategy, "slaves-count");
        // выбираем характеристику по expected-relative-yield
        StrategyCharacteristic strategyCharacteristicsExpectrdRelativeYield = getCharacteristic(liteStrategy, "expected-relative-yield");
        StrategyCharacteristic strategyCharacteristicsRelativeYield = getCharacteristic(liteStrategy, "relative-yield");
        // выбираем характеристику по short-description
        StrategyCharacteristic strategyCharacteristicsShortDescription = getCharacteristic(liteStrategy, "short-description");
        // выбираем характеристику по master-portfolio-top-positions
        StrategyCharacteristic strategyCharacteristicsMasterPortfolioTopPosition = getCharacteristic(liteStrategy, "master-portfolio-top-positions");
        // выбираем характеристику по owner-description
        StrategyCharacteristic strategyCharacteristicsOwnerDescription = getCharacteristic(liteStrategy, "owner-description");
        //Получаем данные по овнеру из client
        SocialProfile socialProfile = clientService.getClient(strategy.getContract().getClientId()).getSocialProfile();
        //проверяем полученные данные
        assertAll(
            () -> assertThat("идентификатор стратегии не равно", liteStrategy.get(0).getId(), is(strategyId)),
            () -> assertThat("title не равно", liteStrategy.get(0).getTitle(), is(title)),
            () -> assertThat("baseCurrency не равно", liteStrategy.get(0).getBaseCurrency().toString(), is(strategy.getBaseCurrency().toString())),
            () -> assertThat("riskProfile не равно", liteStrategy.get(0).getRiskProfile().toString(), is(strategy.getRiskProfile().toString())),
            () -> assertThat("score не равно", liteStrategy.get(0).getScore(), is(strategy.getScore())),
            () -> assertThat("owner.socialProfile.id не равно", liteStrategy.get(0).getOwner().getSocialProfile().getId().toString(), is(socialProfile.getId())),
            () -> assertThat("owner.socialProfile.nickname не равно", liteStrategy.get(0).getOwner().getSocialProfile().getNickname(), is(socialProfile.getNickname())),
            () -> assertThat("owner.socialProfile.image не равно", liteStrategy.get(0).getOwner().getSocialProfile().getImage().toString(), is(socialProfile.getImage())),
            () -> assertThat("relativeYield не равно", liteStrategy.get(0).getRelativeYield(), is(relativeYield)),
            () -> assertThat("portfolioValues стратегии не равно", liteStrategy.get(0).getPortfolioValues().toString(), is("[]")),
            () -> assertThat("isOverloaded не равен", liteStrategy.get(0).getIsOverloaded(), is(strategy.getOverloaded())),
            () -> assertThat("status стратегии не равен", liteStrategy.get(0).getStatus().toString(), is(strategy.getStatus().toString()))
        );
        Allure.step("проверка Characteristics value",
            () -> assertAll(
                //recommended-base-money-position-quantity
                () -> assertThat("recommended-base-money-position-quantity.value не равно", strategyCharacteristicsBaseMoney.getValue(), is(recommendedBaseMoney)),
                () -> assertThat("recommended-base-money-position-quantity.subtitle не равно", strategyCharacteristicsBaseMoney.getSubtitle(), is("советуем вложить")),
                //slaves-count
                () -> assertThat("slaves-count.value не равно", strategyCharacteristicsSlavesCount.getValue(), is(count.charAt(0) + "\u00A0" + count.substring(1))),
                () -> assertThat("slaves-count.subtitle не равно", strategyCharacteristicsSlavesCount.getSubtitle(), is("подписаны")),
                //expected-relative-yield
                () -> assertThat("expected-relative-yield.value не равен", strategyCharacteristicsExpectrdRelativeYield.getValue(), equalTo(expectedRelativeYieldResult + "% в год")),
                () -> assertThat("expected-relative-yield.subtitle не равен", strategyCharacteristicsExpectrdRelativeYield.getSubtitle(), is("Прогноз автора")),
                //relative-yield
                () -> assertThat("relativeYield.value стратегии не равно", strategyCharacteristicsRelativeYield.getValue(), equalTo(relativeYieldValue)),
                () -> assertThat("relativeYield.subtitle стратегии не равно", strategyCharacteristicsRelativeYield.getSubtitle(), is("за все время")),
                //short-description
                () -> assertThat("short-description.value не равно", strategyCharacteristicsShortDescription.getValue(), is(shortDescription)),
                () -> assertThat("short-description.subtitle не равно", strategyCharacteristicsShortDescription.getSubtitle(), is("Короткое описание")),
                //master-portfolio-top-positions
                () -> assertThat("master-portfolio-top-positions.value не равно", strategyCharacteristicsMasterPortfolioTopPosition.getValue(), is("US0378331005.png")),
                () -> assertThat("master-portfolio-top-positions.subtitle не равно", strategyCharacteristicsMasterPortfolioTopPosition.getSubtitle(), is("Топ торгуемых бумаг")),
                //owner-description
                () -> assertThat("owner-description.value не равно", strategyCharacteristicsOwnerDescription.getValue(), is(ownerDescription)),
                () -> assertThat("owner-description.subtitle не равно", strategyCharacteristicsOwnerDescription.getSubtitle(), is("Описание владельца"))
            ));
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
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
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
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        strategyId = UUID.randomUUID();
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWintContractAndStrategyWithOutProfile(investIdMaster, null, contractIdMaster, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2000, LocalDateTime.now(), "0.3", "0.05", false);
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

    BigDecimal roundDecimal(BigDecimal recommendedBaseMoneyPositionQuantity) {
        BigInteger integer = recommendedBaseMoneyPositionQuantity.setScale(0, RoundingMode.UP).toBigInteger();
        BigInteger mod = integer.mod(BigInteger.valueOf(5000));
        if (mod.compareTo(BigInteger.ZERO) == 0) {
            return new BigDecimal(integer);
        }
        return new BigDecimal(
            integer.add(BigInteger.valueOf(5000)).subtract(mod));
    }

    BigDecimal roundDecimalUSD(BigDecimal recommendedBaseMoneyPositionQuantity) {
        BigInteger integer = recommendedBaseMoneyPositionQuantity.setScale(0, RoundingMode.UP).toBigInteger();
        BigInteger mod = integer.mod(BigInteger.valueOf(100));
        if (mod.compareTo(BigInteger.ZERO) == 0) {
            return new BigDecimal(integer);
        }
        return new BigDecimal(
            integer.add(BigInteger.valueOf(100)).subtract(mod));
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

    void createDateMasterPortfolioTopPositions(UUID strategyId, int days, int hours) {
        List<MasterPortfolioTopPositions.TopPositions> topPositions = new ArrayList<>();
        topPositions.add(MasterPortfolioTopPositions.TopPositions.builder()
            .ticker(instrument.tickerAAPL)
            .tradingClearingAccount(instrument.tradingClearingAccountAAPL)
            .signalsCount(3)
            .build());
        masterPortfolioTopPositions = MasterPortfolioTopPositions.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .positions(topPositions)
            .build();
        masterPortfolioTopPositionsDao.insertIntoMasterPortfolioTopPositions(masterPortfolioTopPositions);
    }


    StrategyCharacteristic getCharacteristic ( List<LiteStrategy> liteStrategies, String characteristicName){
        // выбираем характеристику по owner-description
         StrategyCharacteristic characteristic =
             liteStrategies.get(0).getCharacteristics().stream()
                .filter(strategyCharacteristic -> strategyCharacteristic.getId().equals(characteristicName))
                .collect(Collectors.toList()).get(0);
        return characteristic;
    }


    private List<StrategyTag> buildTags(List<TestsStrategy> tags) {
        return tags.stream()
            .map(TestsStrategy::getId)
            .map(this::buildTag)
            .collect(Collectors.toList());
    }

    private StrategyTag buildTag(String id) {
        StrategyTag strategyTag = new StrategyTag();
        strategyTag.setId(id);
        return strategyTag;
    }

}
