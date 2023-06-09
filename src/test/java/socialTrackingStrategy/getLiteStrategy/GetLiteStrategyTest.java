package socialTrackingStrategy.getLiteStrategy;


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
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioTopPositions;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioValue;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.services.*;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.entities.Profile;
import ru.qa.tinkoff.social.entities.TestsStrategy;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.model.GetLiteStrategyResponse;
import ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.model.StrategyCharacteristic;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@Slf4j
@Epic("GetLiteStrategy - Получение облегченных данных стратегии")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("social-tracking-strategy")
@Tags({@Tag("social-tracking-strategy"), @Tag("GetLiteStrategy")})
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

public class GetLiteStrategyTest {

    @Autowired
    StrategyService strategyService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    ProfileService profileService;
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

    Strategy strategy;
    Profile profile;
    String contractIdMaster;
    MasterPortfolioTopPositions masterPortfolioTopPositions;
    UUID strategyId;
    MasterPortfolioValue masterPortfolioValue;
    StrategyApi strategyApi = ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.invoker
        .ApiClient.api(ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.invoker
            .ApiClient.Config.apiConfig()).strategy();

    BigDecimal expectedRelativeYield = new BigDecimal(10.00);
    String siebelIdMaster;
    String xApiKey = "x-api-key";
    String key = "stp-tracking";
    String quantitySBER = "30";
    String quantityFXDE = "5";
    String quantitySU29009RMFS6 = "7";
    String quantityUSD = "2000";

    Boolean overloadedFalse = false;
    Boolean overloadedTrue = true;

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
        });
    }


    @Test
    @AllureId("1122179")
    @DisplayName("C1122179.GetLiteStrategy.Получение данных торговой стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1122179() throws JsonProcessingException {
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
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2000, LocalDateTime.now(), 1,"0.3", "0.05", overloadedFalse, null,"TEST","TEST11",true,true, null);
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        final int daysAgo = 366;
        LocalDateTime from = LocalDateTime.now().minusDays(daysAgo);
        strategy.setActivationTime(from);
        strategyService.saveStrategy(strategy);
        //добавляем записи о стоимости портфеля за разные периода
        createPortfolioValuesDate(from, date);
        //вызываем метод getLiteStrategy
        GetLiteStrategyResponse getLiteStrategy = strategyApi.getLiteStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategyResponse.class));
        //рассчитываем относительную доходность но основе выбранных точек Values
        BigDecimal relativeYield = (new BigDecimal("109268.75")
            .divide(new BigDecimal("68349.11"), 4, RoundingMode.HALF_UP))
            .subtract(new BigDecimal("1"))
            .multiply(new BigDecimal("100"))
            .setScale(2, BigDecimal.ROUND_HALF_EVEN);
        assertThat("идентификатор стратегии не равно", getLiteStrategy.getId(), is(strategyId));
        assertThat("relativeYield стратегии не равно", getLiteStrategy.getRelativeYield(), is(relativeYield));
        assertThat("Isoverloaded стратегии не равно" + overloadedFalse, getLiteStrategy.getIsOverloaded(), is(overloadedFalse));
        assertThat("activationTime стратегии не равно " + strategy.getActivationTime(), getLiteStrategy.getActivationTime().atZoneSameInstant(ZoneId.of("Europe/Moscow")).toLocalDateTime().toString(),
            is(strategy.getActivationTime().toString().substring(0, 23)));
    }


    @Test
    @AllureId("1132109")
    @DisplayName("C1132109.GetLiteStrategy.Нет стоимости портфеля и подписчиков")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1132109() {
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
        List<TestsStrategy> tagsStrategiesList = new ArrayList<>();
        tagsStrategiesList.add(new TestsStrategy().setId("tinkoff_choice"));
        tagsStrategiesList.add(new TestsStrategy().setId("tinkoff_not_choice"));
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1,"0.3", "0.05", overloadedTrue, expectedRelativeYield,"TEST","TEST11",true,true, tagsStrategiesList);
        //создаем запись  протфеле в кассандре
        List<MasterPortfolio.Position> positionList = new ArrayList<>();
        createMasterPortfolio(contractIdMaster, strategyId, 1, "6259.17", positionList);
        //достаем информацию по профайлу из пульса
        profile = profileService.getProfileBySiebelId(siebelIdMaster);
//        //считаем точку
        List<Double> portfolioValuesPoints = new ArrayList<>();
        GetLiteStrategyResponse getLiteStrategy = strategyApi.getLiteStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategyResponse.class));
        List<StrategyCharacteristic> strategyCharacteristicsSlavesCount =
            getLiteStrategy.getCharacteristics().stream()
                .filter(strategyCharacteristic -> strategyCharacteristic.getId().equals("slaves-count"))
                .collect(Collectors.toList());
        assertThat("идентификатор стратегии не равно", getLiteStrategy.getId(), is(strategyId));
        assertThat("title стратегии не равно", getLiteStrategy.getTitle(), is(title));
        assertThat("baseCurrency стратегии не равно", getLiteStrategy.getBaseCurrency().getValue(), is("rub"));
        assertThat("riskProfile стратегии не равно", getLiteStrategy.getRiskProfile().getValue(), is("conservative"));
        assertThat("score стратегии не равно", getLiteStrategy.getScore(), is(1));
        assertThat("owner id стратегии не равно", getLiteStrategy.getOwner().getSocialProfile().getId(), is(profile.getId()));
        assertThat("owner nickname стратегии не равно", getLiteStrategy.getOwner().getSocialProfile().getNickname(), is(profile.getNickname()));
        assertThat("owner image стратегии не равно", getLiteStrategy.getOwner().getSocialProfile().getImage(), is(profile.getImage()));
        assertThat("relativeYield стратегии не равно", getLiteStrategy.getRelativeYield(), is(new BigDecimal("0")));
        assertThat("portfolioValues стратегии не равно", getLiteStrategy.getPortfolioValues(), is(portfolioValuesPoints));
        assertThat("portfolioValues стратегии не равно", getLiteStrategy.getIsOverloaded(), is(overloadedTrue));
        assertThat("value slaves-count не равно", strategyCharacteristicsSlavesCount.get(0).getValue(),
            is("0"));
        assertThat("subtitle slaves-count не равно", strategyCharacteristicsSlavesCount.get(0).getSubtitle(),
            is("подписаны"));
        assertThat("expected-relative-yield не равен", getLiteStrategy.getCharacteristics().get(1).getValue(), is("10% в год"));
        assertThat("short-description не равно", getLiteStrategy.getCharacteristics().get(3).getValue(), is("TEST"));
        assertThat("owner-description не равно", getLiteStrategy.getCharacteristics().get(4).getValue(), is("TEST11"));
        assertThat("список tags не равен", getLiteStrategy.getTags().get(0).getId(), is(tagsStrategiesList.get(0).getId()));
        assertThat("список tags не равен", getLiteStrategy.getTags().get(1).getId(), is(tagsStrategiesList.get(1).getId()));
        assertThat("кол-во tags != 2", getLiteStrategy.getTags().size(), is(2));
        strategy = strategyService.getStrategy(strategyId);
        assertThat("activationTime стратегии не равно " + strategy.getActivationTime(), getLiteStrategy.getActivationTime().atZoneSameInstant(ZoneId.of("Europe/Moscow")).toLocalDateTime().toString(),
            is(strategy.getActivationTime().toString().substring(0, 23)));
    }


    @Test
    @AllureId("1132257")
    @DisplayName("C1132257.GetLiteStrategy.Получение облегченных данных стратегии.Точки со стоимостью портфеля для графика")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1132257() throws JsonProcessingException {
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
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.frozen, 2, LocalDateTime.now(), 1,"0.3", "0.05", false, null,"TEST","TEST11",true,true, null);
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        final int daysAgo = 366;
        LocalDateTime from = LocalDateTime.now().minusDays(daysAgo);
        strategy.setActivationTime(from);
        strategyService.saveStrategy(strategy);
        //добавляем записи о стоимости портфеля за разные периода
        createPortfolioValuesDate(from, date);
        //создаем запись о стоимости портфеля
        createDateMasterPortfolioTopPositions(strategyId,0,1);
        //считаем точку
        BigDecimal normalizedMinMaxDif = new BigDecimal("99").subtract(new BigDecimal("0"));
        BigDecimal minMaxDif = new BigDecimal("109268.75").subtract(new BigDecimal("68349.11"));
        BigDecimal point = new BigDecimal("96268.75")
            .subtract(new BigDecimal("68349.11"))
            .divide(minMaxDif, 4, RoundingMode.HALF_UP)
            .multiply(normalizedMinMaxDif)
            .add(new BigDecimal("0"));
        List<BigDecimal> portfolioValuesPoints = new ArrayList<>();
        portfolioValuesPoints.add(BigDecimal.valueOf(0));
        portfolioValuesPoints.add(BigDecimal.valueOf(68));
        portfolioValuesPoints.add(BigDecimal.valueOf(99));
        //вызываем метод getLiteStrategy
        GetLiteStrategyResponse getLiteStrategy = strategyApi.getLiteStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategyResponse.class));
        assertThat("идентификатор стратегии не равно", getLiteStrategy.getId(), is(strategyId));
        assertThat("portfolioValues стратегии не равно", getLiteStrategy.getPortfolioValues().toString(), is("[]"));
        assertThat("master-portfolio-top-positions.value не равно", getLiteStrategy.getCharacteristics().get(5).getValue(),is("sber.png,minfin.png"));
        assertThat("master-portfolio-top-positions.subtitle не равно", getLiteStrategy.getCharacteristics().get(5).getSubtitle(),is("Топ торгуемых бумаг"));
        assertThat("status стратегии не равен", getLiteStrategy.getStatus().toString(), is("frozen"));
        assertThat("activationTime стратегии не равно " + strategy.getActivationTime(), getLiteStrategy.getActivationTime().atZoneSameInstant(ZoneId.of("Europe/Moscow")).toLocalDateTime().toString(),
            is(strategy.getActivationTime().toString().substring(0, 23)));
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
    @AllureId("1133268")
    @DisplayName("C1133268.GetLiteStrategy.Характеристики стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1133268(StrategyCurrency strategyCurrency, String symbol, int multiplicity) {
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
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, strategyCurrency, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2000, LocalDateTime.now(), 1,"0.3", "0.05", false, null,"TEST","TEST11",true,true, null);
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        final int daysAgo = 366;
        LocalDateTime from = LocalDateTime.now().minusDays(daysAgo);
        strategy.setActivationTime(from);
        strategyService.saveStrategy(strategy);
        //добавляем записи о стоимости портфеля за разные периода
        createPortfolioValuesDate(from, date);
        //считаем recommended-base-money-position-quantity
        BigDecimal recommendedBaseMoneyPositionQuantity = new BigDecimal("109268.75")
            .add(new BigDecimal("109268.75").multiply(new BigDecimal("0.05")));
        recommendedBaseMoneyPositionQuantity = roundDecimal(recommendedBaseMoneyPositionQuantity, multiplicity);
        String str = String.format("%,d", recommendedBaseMoneyPositionQuantity.intValue());

        String rubbleSymbol = symbol;
        String recommendedBaseMoney = str.replace(",", " ") + " " + rubbleSymbol;
        //вызываем метод getLiteStrategy
        GetLiteStrategyResponse getLiteStrategy = strategyApi.getLiteStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategyResponse.class));
        // выбираем характеристику по recommended-base-money-position-quantity
        List<StrategyCharacteristic> strategyCharacteristicsBaseMoney =
            getLiteStrategy.getCharacteristics().stream()
                .filter(strategyCharacteristic -> strategyCharacteristic.getId().equals("recommended-base-money-position-quantity"))
                .collect(Collectors.toList());
        // выбираем характеристику по slaves-count
        List<StrategyCharacteristic> strategyCharacteristicsSlavesCount =
            getLiteStrategy.getCharacteristics().stream()
                .filter(strategyCharacteristic -> strategyCharacteristic.getId().equals("slaves-count"))
                .collect(Collectors.toList());
        assertThat("идентификатор стратегии не равно", getLiteStrategy.getId(), is(strategyId));
        assertThat("value recommended-base-money-position-quantity не равно", strategyCharacteristicsBaseMoney.get(0).getValue(),
            is(recommendedBaseMoney));
        assertThat("subtitle recommended-base-money-position-quantity не равно", strategyCharacteristicsBaseMoney.get(0).getSubtitle(),
            is("советуем вложить"));
        assertThat("value slaves-count не равно", strategyCharacteristicsSlavesCount.get(0).getValue(),
            is("2" + "\u00A0" + "000"));
        assertThat("subtitle slaves-count не равно", strategyCharacteristicsSlavesCount.get(0).getSubtitle(),
            is("подписаны"));
    }


    @Test
    @AllureId("1133646")
    @DisplayName("C1133646.GetLiteStrategy.Данные о стратегии и о владельце стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1133646() throws JsonProcessingException {
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
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2000, LocalDateTime.now(), 1,"0.3", "0.05", false, null,"TEST","TEST11",true,true, null);
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        final int daysAgo = 366;
        LocalDateTime from = LocalDateTime.now().minusDays(daysAgo);
        strategy.setActivationTime(from);
        strategyService.saveStrategy(strategy);
        //добавляем записи о стоимости портфеля за разные периода
        createPortfolioValuesDate(from, date);
        //достаем информацию по профайлу из пульса
        profile = profileService.getProfileBySiebelId(siebelIdMaster);
        GetLiteStrategyResponse getLiteStrategy = strategyApi.getLiteStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategyResponse.class));
        assertThat("идентификатор стратегии не равно", getLiteStrategy.getId(), is(strategyId));
        assertThat("title стратегии не равно", getLiteStrategy.getTitle(), is(title));
        assertThat("baseCurrency стратегии не равно", getLiteStrategy.getBaseCurrency().getValue(), is("rub"));
        assertThat("riskProfile стратегии не равно", getLiteStrategy.getRiskProfile().getValue(), is("conservative"));
        assertThat("score стратегии не равно", getLiteStrategy.getScore(), is(1));
        assertThat("owner id стратегии не равно", getLiteStrategy.getOwner().getSocialProfile().getId(), is(profile.getId()));
        assertThat("owner nickname стратегии не равно", getLiteStrategy.getOwner().getSocialProfile().getNickname(), is(profile.getNickname()));
        assertThat("activationTime стратегии не равно " + strategy.getActivationTime(), getLiteStrategy.getActivationTime().atZoneSameInstant(ZoneId.of("Europe/Moscow")).toLocalDateTime().toString(),
            is(strategy.getActivationTime().toString().substring(0, 23)));
        assertThat("вернули не пустой массив tags", getLiteStrategy.getTags().toString(), is("[]"));
    }


    @Test
    @AllureId("1122200")
    @DisplayName("C1122200.GetLiteStrategy.Не найдена стратегия в strategy")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1122200() throws JSONException {
        strategyId = UUID.randomUUID();
        StrategyApi.GetLiteStrategyOper getLiteStrategy = strategyApi.getLiteStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(422));
        getLiteStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getLiteStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("0000-01-B07"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @Test
    @AllureId("1122201")
    @DisplayName("C1122201.GetLiteStrategy.Стратегия в статусе draft")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1122201() throws JSONException {
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
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1,"0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11",true,true, null);
        StrategyApi.GetLiteStrategyOper getLiteStrategy = strategyApi.getLiteStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(422));
        getLiteStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getLiteStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("0000-01-B07"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @Test
    @AllureId("1122199")
    @DisplayName("C1122199.GetLiteStrategy.Валидация входного запроса: X-API-KEY")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1122199() throws JSONException {
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
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2000, LocalDateTime.now(), 1,"0.3", "0.05", false, null,"TEST","TEST11",true,true, null);
        StrategyApi.GetLiteStrategyOper getLiteStrategy = strategyApi.getLiteStrategy()
//            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(401));
        getLiteStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getLiteStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("0000-00-Z99"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно прав"));
    }


    @Test
    @AllureId("1122180")
    @DisplayName("C1122180.GetLiteStrategy.Валидация входного запроса: x-app-name")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1122180() throws JSONException {
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
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2000, LocalDateTime.now(), 1,"0.3", "0.05", false, null,"TEST","TEST11",true,true, null);
        StrategyApi.GetLiteStrategyOper getLiteStrategy = strategyApi.getLiteStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
//            .xAppNameHeader("stp-tracking-api")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(400));
        getLiteStrategy.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getLiteStrategy.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("0000-00-Z99"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @Test
    @AllureId("1905201")
    @DisplayName("C1905201.GetLiteStrategy .Не нашли записи в таблице master_portfolio_value")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения облегченных данных по торговой стратегии.")
    void C1905201() throws JsonProcessingException {
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
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 2000, LocalDateTime.now(), 1,"0.3", "0.05", overloadedFalse, null,"TEST","TEST11",true,true, null);
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        final int daysAgo = 366;
        LocalDateTime from = LocalDateTime.now().minusDays(daysAgo);
        strategy.setActivationTime(from);
        strategyService.saveStrategy(strategy);
        //вызываем метод getLiteStrategy
        GetLiteStrategyResponse getLiteStrategy = strategyApi.getLiteStrategy()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategyResponse.class));
        List<StrategyCharacteristic> getCharacteristic = getLiteStrategy.getCharacteristics().stream()
            .filter(strategyCharacteristic -> strategyCharacteristic.getId().equals("recommended-base-money-position-quantity"))
            .collect(Collectors.toList());
        //Проверяем, что не вернули характеристику
        assertThat("Вернули характеристику recommended-base-money-position-quantity", getCharacteristic.size(), is(0));
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

    BigDecimal roundDecimal(BigDecimal recommendedBaseMoneyPositionQuantity, int multiplicity) {
        BigInteger integer = recommendedBaseMoneyPositionQuantity.setScale(0, RoundingMode.UP).toBigInteger();
        BigInteger mod = integer.mod(BigInteger.valueOf(multiplicity));
        if (mod.compareTo(BigInteger.ZERO) == 0) {
            return new BigDecimal(integer);
        }
        return new BigDecimal(
            integer.add(BigInteger.valueOf(multiplicity)).subtract(mod));
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

}
