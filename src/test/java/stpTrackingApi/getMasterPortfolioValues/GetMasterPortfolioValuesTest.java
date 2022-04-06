package stpTrackingApi.getMasterPortfolioValues;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolioValue;
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
import ru.qa.tinkoff.swagger.tracking.api.AnalyticsApi;
import ru.qa.tinkoff.swagger.tracking.model.GetMasterPortfolioValuesResponse;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Slf4j
@Epic("getMasterPortfolioValues- Получение стоимостей виртуального портфеля")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-api")
@Tags({@Tag("stp-tracking-api"), @Tag("getMasterPortfolioValues")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    SocialDataBaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingApiStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    ApiCreatorConfiguration.class,
})
public class GetMasterPortfolioValuesTest {
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    StrategyService strategyService;
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
    MasterPortfolioValueDao masterPortfolioValueDao;
    @Autowired
    StpSiebel stpSiebel;
    @Autowired
    ApiCreator<AnalyticsApi> analyticsApiCreator;

    String contractIdMaster;
    MasterPortfolioValue masterPortfolioValue;
    Strategy strategy;
    String siebelIdMaster;
    String siebelIdSlave;
    UUID strategyId;
    String description = "стратегия autotest GetMasterPortfolioValues";
    String title;

    @BeforeAll
    void getDataFromAccount() {
        siebelIdMaster = stpSiebel.siebelIdApiMaster;
        siebelIdSlave = stpSiebel.siebelIdApiSlave;
        title = steps.getTitleStrategy();
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
                masterPortfolioValueDao.deleteMasterPortfolioValueByStrategyId(strategyId);
            } catch (Exception e) {
            }
            try {
                steps.createEventInTrackingContractEvent(contractIdMaster);
            } catch (Exception e) {
            }
        });
    }


    @Test
    @AllureId("1113203")
    @DisplayName("C1113203.GetMasterPortfolioValues.Определение относительной доходности")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения стоимостей портфеля ведущего за выбранный временной интервал.")
    void C1113203() throws JsonProcessingException {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(31).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем записи о стоимости портфеля
        createDateMasterPortfolioValue();
        //вызываем метод GetMasterPortfolioValues
        GetMasterPortfolioValuesResponse expecResponse =
            analyticsApiCreator.get().getMasterPortfolioValues()
                .xAppNameHeader("invest")
                .xAppVersionHeader("4.5.6")
                .xPlatformHeader("ios")
                .strategyIdPath(strategyId)
                .xTcsSiebelIdHeader(siebelIdSlave)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(GetMasterPortfolioValuesResponse.class));
        //рассчитываем относительную доходность но основе выбранных точек Values
        BigDecimal relativeYield = (BigDecimal.valueOf(expecResponse.getValues().get(expecResponse.getValues().size() - 1))
            .divide(BigDecimal.valueOf(expecResponse.getValues().get(0)), 4, RoundingMode.HALF_UP))
            .subtract(new BigDecimal("1"))
            .multiply(new BigDecimal("100"))
            .setScale(2, BigDecimal.ROUND_HALF_EVEN);
        assertThat("Значение relativeYield не равно", expecResponse.getRelativeYield(), is(relativeYield));
    }


    @Test
    @AllureId("1113428")
    @DisplayName("C1113428.GetMasterPortfolioValues. Определение относительной доходности с указанием from и limit")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения стоимостей портфеля ведущего за выбранный временной интервал.")
    void C1113428() throws JsonProcessingException {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(31).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем записи о стоимости портфеля
        createDateMasterPortfolioValue();
//        //преобразовываем дату from
        LocalDateTime fromTime = LocalDateTime.now().minusDays(13);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = formatter.format(fromTime);
        //вызываем метод GetMasterPortfolioValues
        GetMasterPortfolioValuesResponse expecResponse = getMasterPortfolioValuesLimitFrom(dateTs, 3);
        //рассчитываем относительную доходность но основе выбранных точек Values
        BigDecimal relativeYield = (BigDecimal.valueOf(expecResponse.getValues().get(expecResponse.getValues().size() - 1))
            .divide(BigDecimal.valueOf(expecResponse.getValues().get(0)), 4, RoundingMode.HALF_UP))
            .subtract(new BigDecimal("1"))
            .multiply(new BigDecimal("100"))
            .setScale(2, BigDecimal.ROUND_HALF_EVEN);
        assertThat("Значение relativeYield не равно", expecResponse.getRelativeYield(), is(relativeYield));
    }


    @Test
    @AllureId("1113429")
    @DisplayName("C1113429.GetMasterPortfolioValues.Определение относительной доходности, найдено менее 2х точек")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения стоимостей портфеля ведущего за выбранный временной интервал.")
    void C1113429() throws JsonProcessingException {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(31).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем записи о стоимости портфеля
        createDateMasterPortfolioValue();
        //преобразовываем дату from
        LocalDateTime fromTime = LocalDateTime.now().minusDays(3);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = formatter.format(fromTime);
        //вызываем метод GetMasterPortfolioValues
        GetMasterPortfolioValuesResponse expecResponse = getMasterPortfolioValuesLimitFrom(dateTs, 3);
        assertThat("Значение relativeYield не равно", expecResponse.getRelativeYield(), is(new BigDecimal("0")));
    }


    @Test
    @AllureId("1113918")
    @DisplayName("C1113918.GetMasterPortfolioValues.Определение относительной доходности, 2 точки")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения стоимостей портфеля ведущего за выбранный временной интервал.")
    void C1113918() throws JsonProcessingException {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(31).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем записи о стоимости портфеля
        createDateMasterPortfolioValue();
        //преобразовываем дату from
        LocalDateTime fromTime = LocalDateTime.now().minusDays(4);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateTs = formatter.format(fromTime);
        //вызываем метод GetMasterPortfolioValues
        GetMasterPortfolioValuesResponse expecResponse = getMasterPortfolioValuesLimitFrom(dateTs, 2);
        BigDecimal relativeYield = (BigDecimal.valueOf(expecResponse.getValues().get(expecResponse.getValues().size() - 1))
            .divide(BigDecimal.valueOf(expecResponse.getValues().get(0)), 4, RoundingMode.HALF_UP))
            .subtract(new BigDecimal("1"))
            .multiply(new BigDecimal("100"))
            .setScale(2, BigDecimal.ROUND_HALF_EVEN);
        assertThat("Значение relativeYield не равно", expecResponse.getRelativeYield(), is(relativeYield));
    }


    @Test
    @AllureId("981546")
    @DisplayName("C981546.GetMasterPortfolioValues.Получение стоимостей виртуального портфеля c указанием limit=1")
    @Subfeature("Успешные сценарии")
    @Description("Метод для получения стоимостей портфеля ведущего за выбранный временной интервал.")
    void C981546() throws JsonProcessingException {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now(), 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        //изменяем время активации стратегии
        strategy = strategyService.getStrategy(strategyId);
        LocalDateTime updateTime = LocalDateTime.now().minusDays(31).minusHours(2);
        strategy.setActivationTime(updateTime);
        strategyService.saveStrategy(strategy);
        //создаем записи о стоимости портфеля
        createDateMasterPortfolioValue();
        //вызываем метод GetMasterPortfolioValues
        GetMasterPortfolioValuesResponse expecResponse =
            analyticsApiCreator.get().getMasterPortfolioValues()
                .xAppNameHeader("invest")
                .xAppVersionHeader("4.5.6")
                .xPlatformHeader("ios")
                .strategyIdPath(strategyId)
                .xTcsSiebelIdHeader(siebelIdSlave)
                .limitQuery(1)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(GetMasterPortfolioValuesResponse.class));
        double relativeYield = 0.0;
        assertThat("relativeYield не равно", expecResponse.getRelativeYield().doubleValue(), is(relativeYield));
        assertThat("Количество values не равно", expecResponse.getValues().size(), is(1));
    }


    private static Stream<Arguments> provideRequiredParam() {
        return Stream.of(
            Arguments.of(null, "4.5.6", "android"),
            Arguments.of("trading-invest", null, "android"),
            Arguments.of("trading-invest", "4.5.6", null));
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideRequiredParam")
    @AllureId("1113987")
    @DisplayName("C1113987.GetMasterPortfolioValues.Валидация обязательных параметров: x-app-name, x-app-version, x-platform")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения стоимостей портфеля ведущего за выбранный временной интервал.")
    void C1113987(String name, String version, String platform) {
        strategyId = UUID.randomUUID();
        AnalyticsApi.GetMasterPortfolioValuesOper getMasterPortfolioValues = analyticsApiCreator.get().getMasterPortfolioValues()
            .xTcsSiebelIdHeader(siebelIdSlave)
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(400));
        if (name != null) {
            getMasterPortfolioValues = getMasterPortfolioValues.xAppNameHeader(name);
        }
        if (version != null) {
            getMasterPortfolioValues = getMasterPortfolioValues.xAppVersionHeader(version);
        }
        if (platform != null) {
            getMasterPortfolioValues = getMasterPortfolioValues.xPlatformHeader(platform);
        }
        getMasterPortfolioValues.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterPortfolioValues.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("996561")
    @DisplayName("C996561.GetMasterPortfolioValues.Валидация запроса: не передан заголовок x-tcs-siebel-id")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения стоимостей портфеля ведущего за выбранный временной интервал.")
    void C996561() {
        strategyId = UUID.randomUUID();
        // вызываем метод CreateSignal
        AnalyticsApi.GetMasterPortfolioValuesOper getMasterPortfolioValues = analyticsApiCreator.get().getMasterPortfolioValues()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .strategyIdPath(strategyId)
            .respSpec(spec -> spec.expectStatusCode(401));
        getMasterPortfolioValues.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterPortfolioValues.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("InsufficientPrivileges"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Недостаточно прав"));
    }


    @SneakyThrows
    @Test
    @AllureId("981551")
    @DisplayName("C981551.GetMasterPortfolioValues.Передан несуществующий siebel_id")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения стоимостей портфеля ведущего за выбранный временной интервал.")
    void C981551() {
        strategyId = UUID.randomUUID();
        // вызываем метод CreateSignal
        AnalyticsApi.GetMasterPortfolioValuesOper getMasterPortfolioValues = analyticsApiCreator.get().getMasterPortfolioValues()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .strategyIdPath(strategyId)
            .xTcsSiebelIdHeader("6-RGHKKZA6")
            .respSpec(spec -> spec.expectStatusCode(422));
        getMasterPortfolioValues.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterPortfolioValues.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    @SneakyThrows
    @Test
    @AllureId("996507")
    @DisplayName("C996507.GetMasterPortfolioValues.Статус стратегии отличен от 'active', strategy status = 'draft'")
    @Subfeature("Альтернативные сценарии")
    @Description("Метод для получения стоимостей портфеля ведущего за выбранный временной интервал.")
    void C996507() {
        strategyId = UUID.randomUUID();
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = steps.getBrokerAccounts(siebelIdMaster);
        UUID investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        steps.createClientWithContractAndStrategy(siebelIdMaster, investIdMaster, null, contractIdMaster, null, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.rub, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.draft, 0, null, 1, "0.2", "0.04", false, new BigDecimal(58.00), "TEST", "TEST11");
        // вызываем метод CreateSignal
        AnalyticsApi.GetMasterPortfolioValuesOper getMasterPortfolioValues = analyticsApiCreator.get().getMasterPortfolioValues()
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .strategyIdPath(strategyId)
            .xTcsSiebelIdHeader(siebelIdSlave)
            .respSpec(spec -> spec.expectStatusCode(422));
        getMasterPortfolioValues.execute(ResponseBodyData::asString);
        JSONObject jsonObject = new JSONObject(getMasterPortfolioValues.execute(ResponseBodyData::asString));
        String errorCode = jsonObject.getString("errorCode");
        String errorMessage = jsonObject.getString("errorMessage");
        assertThat("код ошибки не равно", errorCode, is("Error"));
        assertThat("Сообщение об ошибке не равно", errorMessage, is("Сервис временно недоступен"));
    }


    void createDateMasterPortfolioValue(UUID strategyId, int days, int hours, String value) {
        masterPortfolioValue = MasterPortfolioValue.builder()
            .strategyId(strategyId)
            .cut(Date.from(OffsetDateTime.now().minusDays(days).minusHours(hours).toInstant()))
            .value(new BigDecimal(value))
            .build();
        masterPortfolioValueDao.insertIntoMasterPortfolioValue(masterPortfolioValue);
    }


    GetMasterPortfolioValuesResponse getMasterPortfolioValuesLimitFrom(String dateTs, int limit) {
        GetMasterPortfolioValuesResponse expecResponse =
            analyticsApiCreator.get().getMasterPortfolioValues()
                .xAppNameHeader("invest")
                .xAppVersionHeader("4.5.6")
                .xPlatformHeader("ios")
                .strategyIdPath(strategyId)
                .xTcsSiebelIdHeader(siebelIdSlave)
                .limitQuery(limit)
                .fromQuery(dateTs)
                .respSpec(spec -> spec.expectStatusCode(200))
                .execute(response -> response.as(GetMasterPortfolioValuesResponse.class));
        return expecResponse;
    }

    void createDateMasterPortfolioValue() {
        //создаем записи о стоимости портфеля
        createDateMasterPortfolioValue(strategyId, 30, 3, "174478.05");
        createDateMasterPortfolioValue(strategyId, 29, 4, "304896.31");
        createDateMasterPortfolioValue(strategyId, 25, 2, "198478.67");
        createDateMasterPortfolioValue(strategyId, 15, 1, "199580.35");
        createDateMasterPortfolioValue(strategyId, 12, 4, "283895.42");
        createDateMasterPortfolioValue(strategyId, 10, 1, "177213.69");
        createDateMasterPortfolioValue(strategyId, 7, 1, "77886.12");
        createDateMasterPortfolioValue(strategyId, 5, 3, "96845.36");
        createDateMasterPortfolioValue(strategyId, 4, 2, "103491.11");
        createDateMasterPortfolioValue(strategyId, 3, 5, "107269.99");
        createDateMasterPortfolioValue(strategyId, 2, 4, "112684.75");
    }

}
