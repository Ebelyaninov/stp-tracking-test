package stpTrackingAdminApi.getSignals;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.AdminApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.SignalApiAdminCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterSignal;
import ru.qa.tinkoff.investTracking.services.MasterSignalDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingAdminStepsConfiguration;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.trackingAdminSteps.StpTrackingAdminSteps;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.swagger.investAccountPublic.model.GetBrokerAccountsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.model.GetSignalsResponse;
import ru.qa.tinkoff.swagger.tracking_admin.model.Signal;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.enums.ContractState;
import ru.qa.tinkoff.tracking.entities.enums.StrategyCurrency;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Epic("getSignals - Получение списка сигналов на стратегии")
@Feature("TAP-13486")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-admin")
@Subfeature("Успешные сценарии")
@Tags({@Tag("stp-tracking-admin"), @Tag("getSignals")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    KafkaAutoConfiguration.class,
    StpTrackingAdminStepsConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    AdminApiCreatorConfiguration.class,
    ApiCreatorConfiguration.class
})
public class GetSignalsTest {
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    StpTrackingAdminSteps stpTrackingAdminSteps;
    @Autowired
    StrategyService strategyService;
    @Autowired
    MasterSignalDao masterSignalDao;
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpSiebel siebel;
    @Autowired
    SignalApiAdminCreator signalApiAdminCreator;

    String xApiKey = "x-api-key";
    String key = "tracking";
    String keyRead = "tcrm";
    String contractIdMaster;
    UUID strategyId;
    LocalDateTime localDateTime;
    UUID investIdMaster;

    @BeforeAll
    void getDataFromAccount() {
        //получаем данные по клиенту master в api сервиса счетов
        GetBrokerAccountsResponse resAccountMaster = stpTrackingAdminSteps.getBrokerAccounts(siebel.siebelIdMasterAdmin);
        investIdMaster = resAccountMaster.getInvestId();
        contractIdMaster = resAccountMaster.getBrokerAccounts().get(0).getId();
        stpTrackingAdminSteps.deleteDataFromDb(siebel.siebelIdMasterAdmin);
    }

    @BeforeEach
    void createClient() {
        strategyId = UUID.randomUUID();
        localDateTime = LocalDateTime.now();
        int randomNumber = 0 + (int) (Math.random() * 1000);
        String title = "Autotest" + String.valueOf(randomNumber);
        String description = "new test стратегия autotest";
        //создаем в БД tracking данные: client, contract, strategy в статусе active
        stpTrackingAdminSteps.createClientWithContractAndStrategy(siebel.siebelIdMasterAdmin, investIdMaster, null, contractIdMaster, ContractState.untracked,
            strategyId, stpTrackingAdminSteps.getTitleStrategy(), description, StrategyCurrency.usd, ru.qa.tinkoff.tracking.entities.enums.StrategyRiskProfile.conservative,
            StrategyStatus.active, 0, LocalDateTime.now().minusDays(1), 1, new BigDecimal(10.00), "TEST",
            "OwnerTEST", true, true, false, "0.2", "0.04", null);

    }

    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                masterSignalDao.deleteMasterSignalByStrategy(strategyId);
            } catch (Exception e) {
            }
            try {
                strategyService.deleteStrategy(stpTrackingAdminSteps.strategy);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(stpTrackingAdminSteps.contract);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(stpTrackingAdminSteps.client);
            } catch (Exception e) {
            }
        });
    }


    @SneakyThrows
    @Test
    @AllureId("1458345")
    @DisplayName("C1458345.GetSignals.Получение списка сигналов без указания cursor & limit")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка сигналов на стратегии")
    void C1458345() {

        createMasterSignal(0, 3, 1, strategyId, instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            "4289.37", "10", 11);
        createMasterSignal(0, 2, 2, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "128.37", "3", 12);
        createMasterSignal(0, 1, 3, strategyId, instrument.tickerFB, instrument.tradingClearingAccountFB,
            "500.37", "10", 13);

        List<MasterSignal> getAllMasterSignals = masterSignalDao.getAllMasterSignal(strategyId);
        GetSignalsResponse getdataFromResponce = getSignalsResponse(strategyId);
        //Сортируем версию по убыванию
        getAllMasterSignals.stream()
            .sorted(Comparator.comparing(MasterSignal::getVersion).reversed())
            .collect(Collectors.toList());

        //Получаем items из ответа метода и БД, для инструментов
        List<Signal> getItemsForSBER = getdataFromResponce.getItems().stream()
            .filter(res -> res.getExchangePosition().getTicker().equals(instrument.tickerSBER))
            .collect(Collectors.toList());

        Optional<MasterSignal> getDataFromDaoForSBER = getAllMasterSignals.stream()
            .filter(signals -> signals.getTicker().equals(instrument.tickerSBER))
            .findFirst();

        List<Signal> getItemsForAAPL = getdataFromResponce.getItems().stream()
            .filter(res -> res.getExchangePosition().getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());

        Optional<MasterSignal> getDataFromDaoForAPPL = getAllMasterSignals.stream()
            .filter(signals -> signals.getTicker().equals(instrument.tickerAAPL))
            .findFirst();

        assertThat("nextCursor != 1", getdataFromResponce.getNextCursor(),
            is(getAllMasterSignals.get(2).getVersion().toString()));
        assertThat("hasNext != false", getdataFromResponce.getHasNext(), is(false));
        //Проверяем выгрузку 2 items c action = 13 игнорируем
        assertThat("получили больше 2 items в ответе метода", getdataFromResponce.getItems().size(),
            is(2));

        checkItemsFromResponce(getItemsForAAPL, getDataFromDaoForAPPL, "buy", StrategyCurrency.usd);
        checkItemsFromResponce(getItemsForSBER, getDataFromDaoForSBER, "sell", StrategyCurrency.usd);
    }


    @SneakyThrows
    @Test
    @AllureId("1705734")
    @DisplayName("C1705734.GetSignals.Получение списка сигналов c api-key доступом read")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка сигналов на стратегии")
    void C1705734() {
        createMasterSignal(0, 3, 1, strategyId, instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            "4289.37", "10", 11);
        createMasterSignal(0, 2, 2, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "128.37", "3", 12);
        createMasterSignal(0, 1, 3, strategyId, instrument.tickerFB, instrument.tradingClearingAccountFB,
            "500.37", "10", 13);
        List<MasterSignal> getAllMasterSignals = masterSignalDao.getAllMasterSignal(strategyId);
        GetSignalsResponse getdataFromResponce = signalApiAdminCreator.get().getSignals()
            .reqSpec(r -> r.addHeader(xApiKey, keyRead))
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("login")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetSignalsResponse.class));
        //Сортируем версию по убыванию
        getAllMasterSignals.stream()
            .sorted(Comparator.comparing(MasterSignal::getVersion).reversed())
            .collect(Collectors.toList());

        //Получаем items из ответа метода и БД, для инструментов
        List<Signal> getItemsForSBER = getdataFromResponce.getItems().stream()
            .filter(res -> res.getExchangePosition().getTicker().equals(instrument.tickerSBER))
            .collect(Collectors.toList());

        Optional<MasterSignal> getDataFromDaoForSBER = getAllMasterSignals.stream()
            .filter(signals -> signals.getTicker().equals(instrument.tickerSBER))
            .findFirst();

        List<Signal> getItemsForAAPL = getdataFromResponce.getItems().stream()
            .filter(res -> res.getExchangePosition().getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());

        Optional<MasterSignal> getDataFromDaoForAPPL = getAllMasterSignals.stream()
            .filter(signals -> signals.getTicker().equals(instrument.tickerAAPL))
            .findFirst();

        assertThat("nextCursor != 1", getdataFromResponce.getNextCursor(),
            is(getAllMasterSignals.get(2).getVersion().toString()));
        assertThat("hasNext != false", getdataFromResponce.getHasNext(), is(false));
        //Проверяем выгрузку 2 items c action = 13 игнорируем
        assertThat("получили больше 2 items в ответе метода", getdataFromResponce.getItems().size(),
            is(2));

        checkItemsFromResponce(getItemsForAAPL, getDataFromDaoForAPPL, "buy", StrategyCurrency.usd);
        checkItemsFromResponce(getItemsForSBER, getDataFromDaoForSBER, "sell", StrategyCurrency.usd);
    }


    @SneakyThrows
    @Test
    @AllureId("1458351")
    @DisplayName("C1458351.GetSignals.Проверка курсора и флага hasNext")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка сигналов на стратегии")
    void C1458351() {

        GetSignalsResponse getSignalsResponse;

        createMasterSignal(0, 3, 1, strategyId, instrument.tickerSBER, instrument.tradingClearingAccountSBER,
            "4289.37", "10", 11);
        createMasterSignal(0, 2, 2, strategyId, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL,
            "128.37", "3", 12);
        createMasterSignal(0, 1, 3, strategyId, instrument.tickerFB, instrument.tradingClearingAccountFB,
            "500.37", "10", 11);
        createMasterSignal(0, 0, 4, strategyId, instrument.tickerNOK, instrument.tradingClearingAccountNOK,
            "500.37", "10", 12);

        List<MasterSignal> getAllMasterSignals = masterSignalDao.getAllMasterSignal(strategyId);
        //Сортируем версию по убыванию
        getAllMasterSignals.stream()
            .sorted(Comparator.comparing(MasterSignal::getVersion).reversed())
            .collect(Collectors.toList());

        //Вызываем метод с курсором 1
        getSignalsResponse = getSignalsResponseWithLimitAndCursor(strategyId, "1", 1);
        //Проверяем курсор
        checkCursoreAndHasNextCursor(getSignalsResponse, "null", false);
        assertThat("items != []", getSignalsResponse.getItems().toString(), is("[]"));

        //Повторно вызываем метод с курсором 2
        getSignalsResponse = getSignalsResponseWithLimitAndCursor(strategyId, "2", 1);

        checkCursoreAndHasNextCursor(getSignalsResponse, "1", false);
        //Получаем items из ответа метода и БД, для инструмента
        List<Signal> getItemsForSBER = getSignalsResponse.getItems().stream()
            .filter(res -> res.getExchangePosition().getTicker().equals(instrument.tickerSBER))
            .collect(Collectors.toList());

        Optional<MasterSignal> getDataFromDaoForSBER = getAllMasterSignals.stream()
            .filter(signals -> signals.getTicker().equals(instrument.tickerSBER))
            .findFirst();

        //Проверяем items
        checkItemsFromResponce(getItemsForSBER, getDataFromDaoForSBER, "sell", StrategyCurrency.usd);
        //Повторно вызываем метод с курсором 3
        getSignalsResponse = getSignalsResponseWithLimitAndCursor(strategyId, "3", 1);

        //Проверяем курсор и items
        checkCursoreAndHasNextCursor(getSignalsResponse, "2", true);
        //Получаем items из ответа метода и БД, для инструмента
        List<Signal> getItemsForAAPL = getSignalsResponse.getItems().stream()
            .filter(res -> res.getExchangePosition().getTicker().equals(instrument.tickerAAPL))
            .collect(Collectors.toList());

        Optional<MasterSignal> getDataFromDaoForAAPL = getAllMasterSignals.stream()
            .filter(signals -> signals.getTicker().equals(instrument.tickerAAPL))
            .findFirst();
        //Проверяем items
        checkItemsFromResponce(getItemsForAAPL, getDataFromDaoForAAPL, "buy", StrategyCurrency.usd);
    }

    @SneakyThrows
    @Test
    @AllureId("1458356")
    @DisplayName("С1458356.Не нашли сигналы по стратегии")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка сигналов на стратегии")
    void C1458356() {
        //Вызываем метод
        GetSignalsResponse getSignalsResponse = getSignalsResponse(strategyId);
        //Проверяем курсор
        checkCursoreAndHasNextCursor(getSignalsResponse, "null", false);
        assertThat("items != []", getSignalsResponse.getItems().toString(), is("[]"));
    }

    @SneakyThrows
    @Test
    @AllureId("1458358")
    @DisplayName("C1458358. Проверка настроек max-limit & default-limit")
    @Subfeature("Успешные сценарии")
    @Description("Получение списка сигналов на стратегии")
    void C1458358() {

        GetSignalsResponse getSignalsResponse;

        for (int i = 0; i < 110; i++) {
            createMasterSignal(0, 3, i, strategyId, instrument.tickerSBER, instrument.tradingClearingAccountSBER,
                "4289.37", "10", 11);
        }
        getSignalsResponse = getSignalsResponse(strategyId);
        //Проверяем, что вернули дефотный лимит 30
        assertThat("getItems().size() > 30", getSignalsResponse.getItems().size(), is(30));
        //Вызываем метод с limit > 100
        getSignalsResponse = getSignalsResponseWithLimitAndCursor(strategyId, "110", 110);
        //Проверяем, что вернули максимальный лимит 100
        assertThat("getItems().size() > 100", getSignalsResponse.getItems().size(), is(100));
    }


    void createMasterSignal(int minusDays, int minusHours, int version, UUID strategyId, String ticker, String tradingClearingAccount,
                            String price, String quantity, int action) {
        LocalDateTime newLocalDateTime = localDateTime.minusDays(minusDays).minusHours(minusHours);
        Date convertedDatetime = Date.from(newLocalDateTime.atZone(ZoneId.systemDefault()).toInstant());
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

    GetSignalsResponse getSignalsResponse(UUID strategyId) {
        GetSignalsResponse getSignalsResponse = signalApiAdminCreator.get().getSignals()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("login")
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetSignalsResponse.class));
        return getSignalsResponse;
    }

    GetSignalsResponse getSignalsResponseWithLimitAndCursor(UUID strategyId, String cursor, int limit) {
        GetSignalsResponse getSignalsResponse = signalApiAdminCreator.get().getSignals()
            .reqSpec(r -> r.addHeader(xApiKey, key))
            .strategyIdPath(strategyId)
            .xAppNameHeader("invest")
            .xAppVersionHeader("4.5.6")
            .xPlatformHeader("ios")
            .xTcsLoginHeader("login")
            .cursorQuery(cursor)
            .limitQuery(limit)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response.as(GetSignalsResponse.class));
        return getSignalsResponse;
    }

    void checkItemsFromResponce(List<Signal> items, Optional<MasterSignal> masterSignal, String action, StrategyCurrency currency) {

        OffsetDateTime offsetDateTime = masterSignal.get().getCreatedAt().toInstant().atOffset(ZoneOffset.ofHours(3));
        assertThat("createdAt != " + offsetDateTime, items.get(0).getCreatedAt().toInstant().atOffset(ZoneOffset.ofHours(3)),
            is(offsetDateTime));
        assertThat("quantity != " + masterSignal.get().getQuantity(), items.get(0).getQuantity(),
            is(masterSignal.get().getQuantity()));
        assertThat("action != " + action, items.get(0).getAction().toString(),
            is(action));
        assertThat("price.value  != " + masterSignal.get().getPrice(), items.get(0).getPrice().getValue(),
            is(masterSignal.get().getPrice()));
        assertThat("price.currency " + currency, items.get(0).getPrice().getCurrency().toString(),
            is(currency.toString()));
        assertThat("exchangePosition.ticker != " + masterSignal.get().getTicker(), items.get(0).getExchangePosition().getTicker(),
            is(masterSignal.get().getTicker()));
        assertThat("exchangePosition.tradingClearingAccount != " + masterSignal.get().getTradingClearingAccount(), items.get(0).getExchangePosition().getTradingClearingAccount(),
            is(masterSignal.get().getTradingClearingAccount()));
        assertThat("version != " + masterSignal.get().getVersion(), items.get(0).getVersion(),
            is(masterSignal.get().getVersion()));
    }

    void checkCursoreAndHasNextCursor(GetSignalsResponse getSignalsResponse, String nextCursor, Boolean hasNext) {
        if (nextCursor == "null") {
            nextCursor = null;
        }
        assertThat("nextCursor != " + nextCursor, getSignalsResponse.getNextCursor(),
            is(nextCursor));
        assertThat("hasNext != " + hasNext, getSignalsResponse.getHasNext(), is(hasNext));
    }
}
