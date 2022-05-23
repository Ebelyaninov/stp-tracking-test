package stpTrackingApi.getStrategiesCatalog;


import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
import ru.qa.tinkoff.creator.CacheApiApiCreator;
import ru.qa.tinkoff.creator.StrategySocialApiCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioValue;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioValueDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingApiStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingApiSteps.StpTrackingApiSteps;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking.api.StrategyApi;
import ru.qa.tinkoff.swagger.tracking.model.GetStrategiesCatalogResponse;
import ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.model.Currency;
import ru.qa.tinkoff.swagger.tracking_socialTrackingStrategy.model.*;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@Slf4j
@Epic("getStrategiesCatalog - Получение каталога стратегий")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@Tags({@Tag("stp-tracking-api"), @Tag("getStrategiesCatalog")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class,
})
public class GetStrategiesCatalogTest {
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
    StpSiebel stpSiebel;
    @Autowired
    ApiCreator<StrategyApi> strategyApiCreator;
    @Autowired
    StrategySocialApiCreator strategySocialApiCreator;
    @Autowired
    CacheApiApiCreator cacheApiApiCreator;

    private Random random = new Random();
    String contractIdMaster;
    UUID strategyId;
    String siebelIdMaster1;
    String siebelIdMaster2;
    String siebelIdMaster3;
    String xApiKey = "x-api-key";
    String key = "stp-tracking";
    MasterPortfolioValue masterPortfolioValue;
    String title;
    String description;
    UUID investIdMaster;


    @BeforeAll
    void conf() {
        siebelIdMaster1 = stpSiebel.siebelIdApiMaster;
        siebelIdMaster2 = stpSiebel.siebelIdApiSlave;
        siebelIdMaster3 = stpSiebel.siebelIdMasterAdmin;
        title = steps.getTitleStrategy();
        description = "стратегия autotest GetStrategiesCatalog";
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster1);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        steps.deleteDataFromDb(siebelIdMaster1);
        steps.deleteDataFromDb(siebelIdMaster2);
        steps.deleteDataFromDb(siebelIdMaster3);
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
        });
    }

    private static Stream<Arguments> provideRequiredParam() {
        return Stream.of(
            Arguments.of(null, "4.5.6", "android"),
            Arguments.of("trading-invest", null, "android"),
            Arguments.of("trading-invest", "4.5.6", null)
        );
    }


    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideRequiredParam")
    @AllureId("535364")
    @DisplayName("1098183.GetStrategiesCatalog.Валидация входного запроса, проверка обязательных параметров: x-app-name, x-app-version, x-platform")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения каталога торговых стратегий.")
    void C1098183(String name, String version, String platform) {
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster1, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        //вызываем метод для получения каталога торговых стратегий getStrategiesCatalog
        StrategyApi.GetStrategiesCatalogOper getStrategiesCatalog = strategyApiCreator.get().getStrategiesCatalog()
            .xTcsSiebelIdHeader(siebelIdMaster2)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            getStrategiesCatalog = getStrategiesCatalog.xAppNameHeader(name);
        }
        if (version != null) {
            getStrategiesCatalog = getStrategiesCatalog.xAppVersionHeader(version);
        }
        if (platform != null) {
            getStrategiesCatalog = getStrategiesCatalog.xPlatformHeader(platform);
        }
        //получаем ответ и проверяем errorCode и Error ошибки
        getStrategiesCatalog.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getStrategiesCatalog.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }

    @SneakyThrows
    @Test
    @AllureId("1098642")
    @DisplayName("C1098642.GetStrategiesCatalog.Валидация обязательных параметров: x-tcs-siebel-id")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения каталога торговых стратегий.")
    void C1098642() {
        //находим в активных подписках договор и стратегию
        strategyId = UUID.randomUUID();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster1, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        //вызываем метод для получения каталога торговых стратегий getStrategiesCatalog
        StrategyApi.GetStrategiesCatalogOper getStrategiesCatalog = strategyApiCreator.get().getStrategiesCatalog()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .respSpec(spec -> spec.expectStatusCode(401));
        //получаем ответ и проверяем errorCode и Error ошибки
        getStrategiesCatalog.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getStrategiesCatalog.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("InsufficientPrivileges"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно прав"));

    }


    @SneakyThrows
    @Test
    @AllureId("1100053")
    @DisplayName("C1100053.GetStrategiesCatalog.Не удалось получить clientId из кеш clientIdCache")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения каталога торговых стратегий.")
    void C1100053() {
        strategyId = UUID.randomUUID();
        steps.createClientWithContractAndStrategy(siebelIdMaster1, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1,"0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        //вызываем метод для получения каталога торговых стратегий getStrategiesCatalog
        StrategyApi.GetStrategiesCatalogOper getStrategiesCatalog = strategyApiCreator.get().getStrategiesCatalog()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader("6-RGHKKZA6")
            .respSpec(spec -> spec.expectStatusCode(422));
        //получаем ответ и проверяем errorCode и Error ошибки
        getStrategiesCatalog.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getStrategiesCatalog.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    private static Stream<Arguments> provideLimit() {
        return Stream.of(
            Arguments.of(1),
            Arguments.of(2),
            Arguments.of(3)
        );
    }

    @ParameterizedTest
    @MethodSource("provideLimit")
    @AllureId("1098182")
    @DisplayName("C1098182.GetStrategiesCatalog.Получение каталога торговых стратегий, передан limit")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения каталога торговых стратегий.")
    void C1098182(Integer limit) {
        //вызываем метод для получения каталога торговых стратегий getStrategiesCatalog
        GetStrategiesCatalogResponse getStrategiesCatalog = strategyApiCreator.get().getStrategiesCatalog()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster2)
            .limitQuery(limit)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategiesCatalogResponse.class));
        //проверяем количество вернувшихся записей
        assertThat("Количество возвращаемых записей не равно", getStrategiesCatalog.getItems()
            .size(), is(limit));
    }


    @Test
    @AllureId("1109331")
    @DisplayName("C1109331.GetStrategiesCatalog.Получение каталога торговых стратегий, фильтр max-slaves-count")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения каталога торговых стратегий.")
    void C1109331() throws InterruptedException {
            //вызываем метод для получения каталога торговых стратегий getStrategiesCatalog
            GetStrategiesCatalogResponse getStrategiesCatalog = strategyApiCreator.get().getStrategiesCatalog()
                .xAppNameHeader("invest")
                .xAppVersionHeader("4.5.6")
                .xPlatformHeader("ios")
                .xTcsSiebelIdHeader(siebelIdMaster2)
                .tabIdQuery("max-slaves-count")
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(GetStrategiesCatalogResponse.class));
            List<Strategy> strategies = strategyService.findListStrategysBySlavesCount();
            //записываем stratedyId в множества и сравниваем их
            Set<UUID> listStrategyIdsFromApi = new HashSet<>();
            for (int i = 0; i < getStrategiesCatalog.getItems().size(); i++) {
                listStrategyIdsFromApi.add(getStrategiesCatalog.getItems().get(i).getId());
            }
            Set<UUID> listStrategyIdsFromDB = new HashSet<>();
            for (int i = 0; i < strategies.size(); i++) {
                listStrategyIdsFromDB.add(strategies.get(i).getId());
            }
            assertThat("идентификаторы стратегий не совпадают", listStrategyIdsFromApi, is(listStrategyIdsFromDB));
    }


    @Test
    @AllureId("1105850")
    @DisplayName("C1105850.GetStrategiesCatalog.Получение каталога торговых стратегий, фильтр conservative-risk-profile")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения каталога торговых стратегий.")
    void C1105850() {
        //вызываем метод для получения каталога торговых стратегий getStrategiesCatalog
        GetStrategiesCatalogResponse getStrategiesCatalog = strategyApiCreator.get().getStrategiesCatalog()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster2)
            .tabIdQuery("conservative-risk-profile")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategiesCatalogResponse.class));
        //вызываем метод getLiteStrategies получения облегченных данных списка торговых стратегий
        GetLiteStrategiesResponse getLiteStrategiesResponse = strategySocialApiCreator.get().getLiteStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategiesResponse.class));
        //выбираем из списка только те стратерии у которых риск-профиль консервативный
        List<LiteStrategy> liteStrategies = getLiteStrategiesResponse.getItems().stream()
            .filter(liteStrategy -> liteStrategy.getRiskProfile() == StrategyRiskProfile.CONSERVATIVE)
            .sorted(new LiteStrategyByScoreAndRelativeYieldComparator().reversed())
            .collect(Collectors.toList());
        //записываем stratedyId в множества и сравниваем их
        Set<UUID> listStrategyIdsFromApi = new HashSet<>();
        for (int i = 0; i < getStrategiesCatalog.getItems().size(); i++) {
            listStrategyIdsFromApi.add(getStrategiesCatalog.getItems().get(i).getId());
        }
        Set<UUID> listStrategyIdsFromSocialApi = new HashSet<>();
        for (int i = 0; i < liteStrategies.size(); i++) {
            listStrategyIdsFromSocialApi.add(liteStrategies.get(i).getId());
        }
        assertThat("идентификаторы стратегий не совпадают", listStrategyIdsFromApi, is(listStrategyIdsFromSocialApi));
    }


    private static Stream<Arguments> provideCurrencyStrategy() {
        return Stream.of(
            Arguments.of("rub-currency", Currency.RUB, StrategyCurrency.rub),
            Arguments.of("usd-currency", Currency.USD, StrategyCurrency.usd)
        );
    }

    @ParameterizedTest
    @MethodSource("provideCurrencyStrategy")
    @AllureId("1105873")
    @DisplayName("C1105873.GetStrategiesCatalog.Получение каталога торговых стратегий, фильтр rub-currency, usd-currency" +
        "подключения к стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения каталога торговых стратегий.")
    void C1105873(String currencyFilter, Currency currency, StrategyCurrency strategyCurrency) throws InterruptedException {
        strategyId = UUID.randomUUID();
//        //создаем в БД tracking данные: client, contract, strategy в статусе active
//        steps.createClientWithContractAndStrategy(siebelIdMaster1, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
//            strategyId, title, description, strategyCurrency, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
//            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        //вызываем метод для получения каталога торговых стратегий getStrategiesCatalog
        GetStrategiesCatalogResponse getStrategiesCatalog = strategyApiCreator.get().getStrategiesCatalog()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster2)
            .tabIdQuery(currencyFilter)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategiesCatalogResponse.class));
        //вызываем метод getLiteStrategies получения облегченных данных списка торговых стратегий
        GetLiteStrategiesResponse getLiteStrategiesResponse = strategySocialApiCreator.get().getLiteStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategiesResponse.class));
        //выбираем из списка только те стратерии у соответствующая валюта и ограничиваем limit из настройки:
        //get-strategies-catalog.default-limit: 30
        List<LiteStrategy> liteStrategies = getLiteStrategiesResponse.getItems().stream()
            .filter(liteStrategy -> liteStrategy.getBaseCurrency() == currency)
            .sorted(new LiteStrategyByScoreAndRelativeYieldComparator().reversed())
            .limit(30)
            .collect(Collectors.toList());
        //записываем stratedyId в множества и сравниваем их
        Set<UUID> listStrategyIdsFromApi = new HashSet<>();
        for (int i = 0; i < getStrategiesCatalog.getItems().size(); i++) {
            listStrategyIdsFromApi.add(getStrategiesCatalog.getItems().get(i).getId());
        }
        Set<UUID> listStrategyIdsFromSocialApi = new HashSet<>();
        for (int i = 0; i < liteStrategies.size(); i++) {
            listStrategyIdsFromSocialApi.add(liteStrategies.get(i).getId());
        }
        assertThat("идентификаторы стратегий не совпадают", listStrategyIdsFromApi, is(listStrategyIdsFromSocialApi));
    }


    @Test
    @AllureId("1109332")
    @DisplayName("C1109332.GetStrategiesCatalog.Получение каталога торговых стратегий, фильтр min-recommended-money-quantity")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения каталога торговых стратегий")
    void C1109332() throws InterruptedException {
//        List<UUID> strategyIds = new ArrayList<>();
//        List<String> contractIds = new ArrayList<>();
//        List<UUID> clientIds = new ArrayList<>();
//        List<String> siebelIds = new ArrayList<>();
//        siebelIds.add(siebelIdMaster1);
//        siebelIds.add(siebelIdMaster2);
//        siebelIds.add(siebelIdMaster3);
//        int slaveCount = 8;
//        try {
//            for (String siebelId : siebelIds) {
//                UUID strategyId = UUID.randomUUID();
//                //randomValue
//                //String title = "Стратегия Autotest - Заголовок";
//                String description = "Стратегия Autotest - Описание";
//                //получаем данные по договор из сервиса счетов
//                GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelId);
//                UUID investId = resAccountMaster.getInvestId();
//                String contractId = resAccountMaster.getBrokerAccounts().get(0).getId();
//                try {
//                    //создаем стратегию
//                    steps.createClientWithContractAndStrategy(siebelId, investId, null, contractId, null, ContractState.untracked,
//                        strategyId, steps.getTitleStrategy(), description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
//                        StrategyStatus.active, slaveCount, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
//                    //создаем данные по стоимости портфеля в диапозоне от 10 тыс. до 26 тыс. за месяц для стратегии
//                    createDateMasterPortfolioValue(strategyId, 31, 3, BigDecimal.valueOf(getRandomDouble(10000, 26000)).toString());
//                    createDateMasterPortfolioValue(strategyId, 25, 2, BigDecimal.valueOf(getRandomDouble(10000, 26000)).toString());
//                    createDateMasterPortfolioValue(strategyId, 15, 4, BigDecimal.valueOf(getRandomDouble(10000, 26000)).toString());
//                    createDateMasterPortfolioValue(strategyId, 13, 1, BigDecimal.valueOf(getRandomDouble(10000, 26000)).toString());
//                    createDateMasterPortfolioValue(strategyId, 12, 4, BigDecimal.valueOf(getRandomDouble(10000, 26000)).toString());
//                    createDateMasterPortfolioValue(strategyId, 10, 1, BigDecimal.valueOf(getRandomDouble(10000, 26000)).toString());
//                    createDateMasterPortfolioValue(strategyId, 7, 1, BigDecimal.valueOf(getRandomDouble(10000, 26000)).toString());
//                    createDateMasterPortfolioValue(strategyId, 5, 3, BigDecimal.valueOf(getRandomDouble(10000, 26000)).toString());
//                } catch (Exception e) {
//                    log.error("завис на создании");
//                }
//                slaveCount = slaveCount + 1;
//                strategyIds.add(strategyId);
//                contractIds.add(contractId);
//                clientIds.add(investId);
//            }

            //вызываем метод для получения каталога торговых стратегий getStrategiesCatalog
            GetStrategiesCatalogResponse getStrategiesCatalog = strategyApiCreator.get().getStrategiesCatalog()
                .xAppNameHeader("invest")
                .xAppVersionHeader("4.5.6")
                .xPlatformHeader("ios")
                .xTcsSiebelIdHeader(siebelIdMaster2)
                .tabIdQuery("min-recommended-money-quantity")
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(GetStrategiesCatalogResponse.class));
            //вызываем метод getLiteStrategies получения облегченных данных списка торговых стратегий
            GetLiteStrategiesResponse getLiteStrategiesResponse = strategySocialApiCreator.get().getLiteStrategies()
                .reqSpec(r -> r.addHeader(xApiKey, key))
                .xAppNameHeader("stp-tracking-api")
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(GetLiteStrategiesResponse.class));

            String price = steps.getPriceFromExchangePositionPriceCache("USDRUB", "MB9885503216", "last", siebelIdMaster2);
            double courseUSD = Double.parseDouble(price);

            //выбираем из списка только те стратерии у есть рекомендованая начальная сумма
            List<LiteStrategy> liteStrategies = getLiteStrategiesResponse.getItems().stream()
                .filter(liteStrategy -> liteStrategy.getCharacteristics().stream()
                    .anyMatch(strategyCharacteristic ->
                        strategyCharacteristic.getId().equals("recommended-base-money-position-quantity")))
                .sorted(new RecommendedBaseMoneyPositionQuantityComparator(courseUSD))
                .collect(Collectors.toList());
            //находим сколько значений попадет в 25й перцентиль = N * 0,25 (где N - количество стратерии)
            BigDecimal valueInPercentile = new BigDecimal("0.25")
                .multiply(BigDecimal.valueOf(liteStrategies.size()))
                //округляем наверх
                .round(new MathContext(1, RoundingMode.UP));
            //определяем значение 2-го элемента
            LiteStrategy targetStrategy = liteStrategies.get(valueInPercentile.intValue() - 1);
            double targetQuantity = RecommendedBaseMoneyPositionQuantityComparator
                .getRecommendedBaseMoneyPositionQuantity(targetStrategy, courseUSD);
            //берем все значения, которые <= значение 2-го элемента
            List<LiteStrategy> liteStrategiesNew = liteStrategies.stream()
                .filter(liteStrategy -> RecommendedBaseMoneyPositionQuantityComparator
                    .getRecommendedBaseMoneyPositionQuantity(liteStrategy, courseUSD) <= targetQuantity)
                .sorted(new LiteStrategyByScoreAndRelativeYieldComparator().reversed())
                .collect(Collectors.toList());
            //записываем stratedyId в множества и сравниваем их
            List<UUID> listStrategyIdsFromApi = new ArrayList<>();
            for (int i = 0; i < getStrategiesCatalog.getItems().size(); i++) {
                listStrategyIdsFromApi.add(getStrategiesCatalog.getItems().get(i).getId());
            }
            List<UUID> listStrategyIdsFromSocialApi = new ArrayList<>();
            for (int i = 0; i < liteStrategiesNew.size(); i++) {
                listStrategyIdsFromSocialApi.add(liteStrategiesNew.get(i).getId());
            }
            assertThat("идентификаторы стратегий не совпадают", listStrategyIdsFromApi, is(listStrategyIdsFromSocialApi));
//        } finally {
//            strategyService.deleteStrategyByIds(strategyIds);
//            contractService.deleteStrategyByIds(contractIds);
//            clientService.deleteStrategyByIds(clientIds);
//            masterPortfolioValueDao.deleteMasterPortfolioValueByStrategyIds(strategyIds);
//        }
    }


    @Test
    @AllureId("1110593")
    @DisplayName("C1110593.GetStrategiesCatalog.Получение каталога торговых стратегий, hasNext, nextCursor")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения каталога торговых стратегий.")
    void C1110593() {
        //вызываем метод getLiteStrategies получения облегченных данных списка торговых стратегий
        GetLiteStrategiesResponse getLiteStrategiesResponse = strategySocialApiCreator.get().getLiteStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategiesResponse.class));
        List<LiteStrategy> liteStrategies = getLiteStrategiesResponse.getItems().stream()
            .sorted(new LiteStrategyByScoreAndRelativeYieldComparator().reversed())
            .collect(Collectors.toList());
        //определяем значение курсора
        UUID cursor = liteStrategies.get(liteStrategies.size() - 2).getId();
        //вызываем метод для получения каталога торговых стратегий getStrategiesCatalog
        GetStrategiesCatalogResponse getStrategiesCatalog = strategyApiCreator.get().getStrategiesCatalog()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster2)
            .cursorQuery(cursor)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategiesCatalogResponse.class));
//            проверяем, данные в сообщении
        assertThat("Идентификатор стратегии не равно", getStrategiesCatalog.getItems().get(0).getId(),
            is(liteStrategies.get(liteStrategies.size() - 1).getId()));
        assertThat("HasNext не равно", getStrategiesCatalog.getHasNext(),
            is(false));
        assertThat("Идентификатор сдедующей стратегии не равно", getStrategiesCatalog.getNextCursor(),
            is(liteStrategies.get(liteStrategies.size() - 1).getId().toString()));
        //определяем значение курсора
        UUID cursorNew = liteStrategies.get(liteStrategies.size() - 3).getId();
        GetStrategiesCatalogResponse getStrategiesCatalogNew = strategyApiCreator.get().getStrategiesCatalog()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster2)
            .cursorQuery(cursorNew)
            .limitQuery(1)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategiesCatalogResponse.class));
//            проверяем, данные в сообщении
        assertThat("HasNext не равно", getStrategiesCatalogNew.getHasNext(),
            is(true));
        assertThat("Идентификатор сдедующей стратегии не равно", getStrategiesCatalogNew.getNextCursor(),
            is(liteStrategies.get(liteStrategies.size() - 2).getId().toString()));
    }


    @Test
    @AllureId("1110503")
    @DisplayName("C1110503.GetStrategiesCatalog.Получение каталога торговых стратегий, передан Сursor")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения каталога торговых стратегий.")
    void C1110503() {
        //вызываем метод getLiteStrategies получения облегченных данных списка торговых стратегий
        GetLiteStrategiesResponse getLiteStrategiesResponse = strategySocialApiCreator.get().getLiteStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategiesResponse.class));
        List<LiteStrategy> liteStrategies = getLiteStrategiesResponse.getItems().stream()
            .sorted(new LiteStrategyByScoreAndRelativeYieldComparator().reversed())
            .collect(Collectors.toList());
        //определяем значение курсора
        UUID cursor = liteStrategies.get(liteStrategies.size() - 2).getId();
        //вызываем метод для получения каталога торговых стратегий getStrategiesCatalog
        GetStrategiesCatalogResponse getStrategiesCatalog = strategyApiCreator.get().getStrategiesCatalog()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster2)
            .cursorQuery(cursor)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategiesCatalogResponse.class));
//            проверяем, данные в сообщении
        assertThat("Идентификатор стратегии не равно", getStrategiesCatalog.getItems().get(0).getId(),
            is(liteStrategies.get(liteStrategies.size() - 1).getId()));
    }

    @Test
    @AllureId("1110525")
    @DisplayName("C1110525.GetStrategiesCatalog.Получение каталога торговых стратегий, параметры ответа")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения каталога торговых стратегий.")
    void C1110525() {
        //вызываем метод getLiteStrategies получения облегченных данных списка торговых стратегий
        GetLiteStrategiesResponse getLiteStrategiesResponse = strategySocialApiCreator.get().getLiteStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategiesResponse.class));
        List<LiteStrategy> liteStrategies = getLiteStrategiesResponse.getItems().stream()
            .sorted(new LiteStrategyByScoreAndRelativeYieldComparator().reversed())
            .collect(Collectors.toList());
        //определяем значение курсора
        UUID cursor = liteStrategies.get(liteStrategies.size() - 2).getId();
        //вызываем метод для получения каталога торговых стратегий getStrategiesCatalog
        GetStrategiesCatalogResponse getStrategiesCatalog = strategyApiCreator.get().getStrategiesCatalog()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster2)
            .cursorQuery(cursor)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategiesCatalogResponse.class));
        // проверяем, данные в ответе
        assertThat("HasNext не равно", getStrategiesCatalog.getHasNext(),
            is(false));
        assertThat("Идентификатор сдедующей стратегии nextCursor не равно", getStrategiesCatalog.getNextCursor(),
            is(liteStrategies.get(liteStrategies.size() - 1).getId().toString()));
        assertThat("Идентификатор стратегии не равно", getStrategiesCatalog.getItems().get(0).getId(),
            is(liteStrategies.get(liteStrategies.size() - 1).getId()));
        assertThat("title стратегии не равно", getStrategiesCatalog.getItems().get(0).getTitle(),
            is(liteStrategies.get(liteStrategies.size() - 1).getTitle()));
        assertThat("baseCurrency стратегии не равно", getStrategiesCatalog.getItems().get(0).getBaseCurrency().getValue(),
            is(liteStrategies.get(liteStrategies.size() - 1).getBaseCurrency().getValue()));
        assertThat("riskProfile стратегии не равно", getStrategiesCatalog.getItems().get(0).getRiskProfile().getValue(),
            is(liteStrategies.get(liteStrategies.size() - 1).getRiskProfile().getValue()));
        assertThat("score стратегии не равно", getStrategiesCatalog.getItems().get(0).getScore(),
            is(liteStrategies.get(liteStrategies.size() - 1).getScore()));
        assertThat("socialProfile.id стратегии не равно", getStrategiesCatalog.getItems().get(0).getOwner().getSocialProfile().getId(),
            is(liteStrategies.get(liteStrategies.size() - 1).getOwner().getSocialProfile().getId()));
        assertThat("socialProfile.nickname стратегии не равно", getStrategiesCatalog.getItems().get(0).getOwner().getSocialProfile().getNickname(),
            is(liteStrategies.get(liteStrategies.size() - 1).getOwner().getSocialProfile().getNickname()));
        assertThat("relativeYield стратегии не равно", getStrategiesCatalog.getItems().get(0).getRelativeYield(),
            is(liteStrategies.get(liteStrategies.size() - 1).getRelativeYield()));
        assertThat("portfolioValues стратегии не равно", getStrategiesCatalog.getItems().get(0).getPortfolioValues(),
            is(liteStrategies.get(liteStrategies.size() - 1).getPortfolioValues()));
        assertThat("characteristics.id стратегии не равно", getStrategiesCatalog.getItems().get(0).getCharacteristics().get(0).getId(),
            is(liteStrategies.get(liteStrategies.size() - 1).getCharacteristics().get(0).getId()));
        assertThat("characteristics.value стратегии не равно", getStrategiesCatalog.getItems().get(0).getCharacteristics().get(0).getValue(),
            is(liteStrategies.get(liteStrategies.size() - 1).getCharacteristics().get(0).getValue()));
        assertThat("characteristics.subtitle стратегии не равно", getStrategiesCatalog.getItems().get(0).getCharacteristics().get(0).getSubtitle(),
            is(liteStrategies.get(liteStrategies.size() - 1).getCharacteristics().get(0).getSubtitle()));
        assertThat("characteristics.expected-relative-yield стратегии не равно", getStrategiesCatalog.getItems().get(0).getCharacteristics().get(2).getValue(),
            is(liteStrategies.get(liteStrategies.size() - 1).getCharacteristics().get(2).getValue()));

    }


    @Test
    @AllureId("1111769")
    @DisplayName("C1111769.GetStrategiesCatalog.Получение каталога торговых стратегий, hasNext is null")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения каталога торговых стратегий.")
    void C1111769() {
        //вызываем метод getLiteStrategies получения облегченных данных списка торговых стратегий
        GetLiteStrategiesResponse getLiteStrategiesResponse = strategySocialApiCreator.get().getLiteStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategiesResponse.class));
        List<LiteStrategy> liteStrategies = getLiteStrategiesResponse.getItems().stream()
            .sorted(new LiteStrategyByScoreAndRelativeYieldComparator().reversed())
            .collect(Collectors.toList());
        //определяем значение курсора
        UUID cursor = liteStrategies.get(liteStrategies.size() - 1).getId();
        //вызываем метод для получения каталога торговых стратегий getStrategiesCatalog
        GetStrategiesCatalogResponse getStrategiesCatalog = strategyApiCreator.get().getStrategiesCatalog()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster2)
            .cursorQuery(cursor)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategiesCatalogResponse.class));
//            проверяем, данные в сообщении
        assertThat("Идентификатор следующей стратегии не равно", getStrategiesCatalog.getNextCursor(),
            is(nullValue()));
    }



    @Test
    @AllureId("1559100")
    @DisplayName("C1559100. Конвертировать все рекомендуемый суммы в рубли")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения каталога торговых стратегий")
    void C1559100() throws InterruptedException {
        //вызываем метод для получения каталога торговых стратегий getStrategiesCatalog
        GetStrategiesCatalogResponse getStrategiesCatalog = strategyApiCreator.get().getStrategiesCatalog()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsSiebelIdHeader(siebelIdMaster2)
            .tabIdQuery("min-recommended-money-quantity")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetStrategiesCatalogResponse.class));
        //вызываем метод getLiteStrategies получения облегченных данных списка торговых стратегий
        GetLiteStrategiesResponse getLiteStrategiesResponse = strategySocialApiCreator.get().getLiteStrategies()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .xAppNameHeader("stp-tracking-api")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetLiteStrategiesResponse.class));
        String price = steps.getPriceFromExchangePositionPriceCache("USDRUB", "MB9885503216", "last", siebelIdMaster2);
        double courseUSD = Double.parseDouble(price);
        //выбираем из списка только те стратерии у есть рекомендованая начальная сумма
        List<LiteStrategy> liteStrategies = getLiteStrategiesResponse.getItems().stream()
            .filter(liteStrategy -> liteStrategy.getCharacteristics().stream()
                .anyMatch(strategyCharacteristic ->
                    strategyCharacteristic.getId().equals("recommended-base-money-position-quantity")))
            .sorted(new RecommendedBaseMoneyPositionQuantityComparator(courseUSD))
            .collect(Collectors.toList());
        int percentile = (int) Math.ceil(25 * liteStrategies.size() / 100);
        var result = new ArrayList<>(liteStrategies.subList(0, percentile));
        Collections.reverse(result);
        //записываем stratedyId в множества и сравниваем их
        List<UUID> listStrategyIdsFromApi = new ArrayList<>();
        for (int i = 0; i < getStrategiesCatalog.getItems().size(); i++) {
            listStrategyIdsFromApi.add(getStrategiesCatalog.getItems().get(i).getId());
        }
        Collections.sort(listStrategyIdsFromApi);
        List<UUID> listStrategyIdsFromSocialApi = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            listStrategyIdsFromSocialApi.add(result.get(i).getId());
        }
        Collections.sort(listStrategyIdsFromSocialApi);
    }
    double getRandomDouble(double from, double to) {
        double v = random.nextDouble();
        return from + (to - from) * v;
    }

    static class LiteStrategyByScoreAndRelativeYieldComparator implements Comparator<LiteStrategy> {

        @Override
        public int compare(LiteStrategy o1, LiteStrategy o2) {
            int compare = Integer.compare(o1.getScore(), o2.getScore());
            if (compare != 0) {
                return compare;
            }
            compare = Double.compare(o1.getRelativeYield().doubleValue(), o2.getRelativeYield().doubleValue());
            if (compare != 0) {
                return compare;
            }
            return compare;
        }

    }

    static class RecommendedBaseMoneyPositionQuantityComparator implements Comparator<LiteStrategy> {

        private final double courseUsdRub;

        public RecommendedBaseMoneyPositionQuantityComparator(double courseUsdRub) {
            this.courseUsdRub = courseUsdRub;
        }

        @Override
        public int compare(LiteStrategy o1, LiteStrategy o2) {
            double v1 = getRecommendedBaseMoneyPositionQuantity(o1, courseUsdRub);
            double v2 = getRecommendedBaseMoneyPositionQuantity(o2, courseUsdRub);
            return Double.compare(v1, v2);
        }

        static double getRecommendedBaseMoneyPositionQuantity(LiteStrategy liteStrategy, double courseUsdRub) {
            double val = liteStrategy.getCharacteristics().stream()
                .filter(strategyCharacteristic -> strategyCharacteristic.getId().equals("recommended-base-money-position-quantity"))
                .map(StrategyCharacteristic::getValue)
                .map(GetStrategiesCatalogTest::convertBaseMoneyPositionQuantity)
                .findFirst()
                .orElseThrow(RuntimeException::new);
            if (liteStrategy.getBaseCurrency() == Currency.USD) {
                val = val * courseUsdRub;
            }
            return val;
        }
    }

    static class LiteStrategyBySlavesCountComparator implements Comparator<LiteStrategy> {

        @Override
        public int compare(LiteStrategy o1, LiteStrategy o2) {
            long v1 = getLiteStrategySlavesCount(o1);
            long v2 = getLiteStrategySlavesCount(o2);
            return Long.compare(v1, v2);
        }


        static long getLiteStrategySlavesCount(LiteStrategy liteStrategy) {
            return liteStrategy.getCharacteristics().stream()
                .filter(strategyCharacteristic -> strategyCharacteristic.getId().equals("slaves-count"))
                .map(strategyCharacteristic -> Long.parseLong(strategyCharacteristic.getValue()))
                .findFirst()
                .orElseThrow(RuntimeException::new);
        }
    }


    void createDateMasterPortfolioValue(UUID strategyId, int days, int hours, String value) {
        masterPortfolioValue = MasterPortfolioValue.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .value(new BigDecimal(value))
            .build();
        masterPortfolioValueDao.insertIntoMasterPortfolioValue(masterPortfolioValue);
    }

    //преобразовываем значение по value recommended-base-money-position-quantity
    static long convertBaseMoneyPositionQuantity(String value) {
        value =
            //удаляем валюту
            value.substring(0, value.length() - 1)
                //удаляем пробелы
                .replaceAll("\\s+", "");
        String valueNew = value.replaceAll("\u00A0", "");
        //переводим из строки в BigDecimal
        return Long.parseLong(valueNew);
    }


}
