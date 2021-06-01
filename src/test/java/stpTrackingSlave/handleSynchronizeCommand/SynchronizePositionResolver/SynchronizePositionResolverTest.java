package stpTrackingSlave.handleSynchronizeCommand.SynchronizePositionResolver;



import com.google.protobuf.Timestamp;
import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.junit5.AllureJunit5;
import io.restassured.response.Response;
import io.restassured.response.ResponseBodyData;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.billing.configuration.BillingDatabaseAutoConfiguration;
import ru.qa.tinkoff.billing.entities.BrokerAccount;
import ru.qa.tinkoff.billing.services.BillingService;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.SlaveOrderDao;
import ru.qa.tinkoff.investTracking.services.SlavePortfolioDao;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream.PriceUpdatedEvent;
import ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream.PriceUpdatedKey;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.swagger.MD.api.PricesApi;
import ru.qa.tinkoff.swagger.tracking.api.SubscriptionApi;
import ru.qa.tinkoff.swagger.tracking.invoker.ApiClient;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.Subscription;
import ru.qa.tinkoff.tracking.entities.enums.*;
import ru.qa.tinkoff.tracking.services.database.*;
import ru.tinkoff.invest.sdet.kafka.protobuf.KafkaProtobufFactoryAutoConfiguration;
import ru.tinkoff.invest.sdet.kafka.protobuf.sender.KafkaProtobufCustomSender;
import ru.tinkoff.trading.tracking.Tracking;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static io.qameta.allure.Allure.step;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Slf4j
@Epic("handleSynchronizeCommand - Выбор позиции для синхронизации")
@Feature("TAP-6844")
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@DisplayName("stp-tracking-slave")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    BillingDatabaseAutoConfiguration.class,
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    KafkaProtobufFactoryAutoConfiguration.class,
    ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration.class
})

public class SynchronizePositionResolverTest {

    @Resource(name = "customSenderFactory")
    KafkaProtobufCustomSender<String, byte[]> kafkaSender;
    @Autowired
    StringSenderService stringSenderService;
    @Autowired
    BillingService billingService;
    @Autowired
    ProfileService profileService;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    SlavePortfolioDao slavePortfolioDao;
    @Autowired
    SlaveOrderDao slaveOrderDao;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ExchangePositionService exchangePositionService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    SubscriptionService subscriptionService;


    SubscriptionApi subscriptionApi = ApiClient.api(ApiClient.Config.apiConfig()).subscription();
    PricesApi pricesApi = ru.qa.tinkoff.swagger.MD.invoker.ApiClient.api(ru.qa.tinkoff.swagger.MD.
        invoker.ApiClient.Config.apiConfig()).prices();
    SlavePortfolio slavePortfolio;
    SlaveOrder slaveOrder;
    Client clientMaster;
    Contract contractMaster;
    Strategy strategy;
    Client clientSlave;
    Contract contractSlave;
    Subscription subscription;
    String contractIdMaster;
    String contractIdSlave;
    UUID strategyId;
    String SIEBEL_ID_MASTER = "4-1V1UVPX8";
    String SIEBEL_ID_SLAVE = "5-LFCI8UPV";


    @AfterEach
    void deleteClient() {
        step("Удаляем клиента автоследования", () -> {
            try {
                subscriptionService.deleteSubscription(subscription);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractSlave);
            } catch (Exception e) {
            }
            try {
                clientSlave = clientService.getClient(clientSlave.getId());
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientSlave);
            } catch (Exception e) {
            }
            try {
                trackingService.deleteStrategy(strategy);
            } catch (Exception e) {
            }
            try {
                contractService.deleteContract(contractMaster);
            } catch (Exception e) {
            }
            try {
                clientService.deleteClient(clientMaster);
            } catch (Exception e) {
            }
            try {
                masterPortfolioDao.deleteMasterPortfolio(contractIdMaster, strategyId);
            } catch (Exception e) {
            }
            try {
                slavePortfolioDao.deleteSlavePortfolio(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
            try {
                slaveOrderDao.deleteSlaveOrder(contractIdSlave, strategyId);
            } catch (Exception e) {
            }
            try {
                createEventInTrackingEvent(contractIdSlave);
            } catch (Exception e) {
            }
        });
    }


    @SneakyThrows
    @Test
    @AllureId("690419")
    @DisplayName("C690419.SynchronizePositionResolver.Выбор позиции.Обрабатываем позиции.Slave_portfolio_position.quantity_diff < 0")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C690419() {
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        String tickerShare1 = "ABBV";
        String tradingClearingAccountShare1 = "L01+00000SPB";
        String classCodeShare1 = "SPBXM";
        String tickerShare2 = "QCOM";
        String tradingClearingAccountShare2 = "L01+00000SPB";
        String classCodeShare2 = "SPBXM";
        BigDecimal lot = new BigDecimal("1");
        createDataToMarketData(tickerShare1, classCodeShare1, "90", "90", "87");
        createDataToMarketData(tickerShare2, classCodeShare2, "55.05", "55.08", "54.82");
        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(tickerShare2)
            .tradingClearingAccount(tradingClearingAccountShare2)
            .quantity(new BigDecimal("10"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(tickerShare1)
            .tradingClearingAccount(tradingClearingAccountShare1)
            .quantity(new BigDecimal("4"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(2, "6259.17", positionListMaster);
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(tickerShare2)
            .tradingClearingAccount(tradingClearingAccountShare2)
            .quantity(new BigDecimal("20"))
            .changedAt(date)
            .build());
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(tickerShare1)
            .tradingClearingAccount(tradingClearingAccountShare1)
            .quantity(new BigDecimal("3"))
            .changedAt(date)
            .build());
        String baseMoneySlave = "6259.17";
        createSlavePortfolioWithOutPosition(1, 1, null, baseMoneySlave, positionListSl);
        //отправляем команду на синхронизацию
        createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (tickerShare2.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        await().atMost(FIVE_SECONDS).until(() ->
            slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId), notNullValue());
//       slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is("1"));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(lots.multiply(lot)));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(tickerShare2));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccountShare2));
    }


    @SneakyThrows
    @Test
    @AllureId("695626")
    @DisplayName("C695626.SynchronizePositionResolver.Обрабатываем позиции.Slave_portfolio_position.quantity_diff < 0 и type из exchangePositionCache = 'share'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C695626() {
        String tickerBond = "VTBperp";
        String tradingClearingAccountBond = "L01+00000SPB";
        String classCodeBond = "SPBBND";
        String tickerShare = "QCOM";
        String tradingClearingAccountShare = "L01+00000SPB";
        String classCodeShare = "SPBXM";
        BigDecimal lot = new BigDecimal("1");
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createDataToMarketData(tickerBond, classCodeBond, "107.2", "108.2", "105.2");
        createDataToMarketData(tickerShare, classCodeShare, "55.05", "55.08", "54.82");
        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
//        создаем команду для топика tracking.event, чтобы очистился кеш contractCache
//        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(tickerShare)
            .tradingClearingAccount(tradingClearingAccountShare)
            .quantity(new BigDecimal("10"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(tickerBond)
            .tradingClearingAccount(tradingClearingAccountBond)
            .quantity(new BigDecimal("400"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(2, "6259.17", positionListMaster);
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(tickerShare)
            .tradingClearingAccount(tradingClearingAccountShare)
            .quantity(new BigDecimal("20"))
            .changedAt(date)
            .build());
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(tickerBond)
            .tradingClearingAccount(tradingClearingAccountBond)
            .quantity(new BigDecimal("600"))
            .changedAt(date)
            .build());
        String baseMoneySlave = "6259.17";
        createSlavePortfolioWithOutPosition(1, 1, null, baseMoneySlave, positionListSl);
        //отправляем команду на синхронизацию
        createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (tickerShare.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is("1"));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(lots.multiply(lot)));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(tickerShare));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccountShare));
    }


    @SneakyThrows
    @Test
    @AllureId("695911")
    @DisplayName("695911.SynchronizePositionResolver.Обрабатываем позиции.Несколько позиций, у которых slave_portfolio_position.quantity_diff < 0 и type из exchangePositionCache = 'share'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C695911() {
        String tickerShare1 = "ABBV";
        String tradingClearingAccountShare1 = "L01+00000SPB";
        String classCodeShare1 = "SPBXM";
        String tickerShare2 = "QCOM";
        String tradingClearingAccountShare2 = "L01+00000SPB";
        String classCodeShare2 = "SPBXM";
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        BigDecimal lot = new BigDecimal("1");
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createDataToMarketData(tickerShare1, classCodeShare1, "90", "90", "87");
        createDataToMarketData(tickerShare2, classCodeShare2, "55.05", "55.08", "54.82");
        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
//        создаем команду для топика tracking.event, чтобы очистился кеш contractCache
//        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(tickerShare2)
            .tradingClearingAccount(tradingClearingAccountShare2)
            .quantity(new BigDecimal("10"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(tickerShare1)
            .tradingClearingAccount(tradingClearingAccountShare1)
            .quantity(new BigDecimal("4"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());

        createMasterPortfolio(2, "6259.17", positionListMaster);
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(tickerShare2)
            .tradingClearingAccount(tradingClearingAccountShare2)
            .quantity(new BigDecimal("20"))
            .changedAt(date)
            .build());
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(tickerShare1)
            .tradingClearingAccount(tradingClearingAccountShare1)
            .quantity(new BigDecimal("6"))
            .changedAt(date)
            .build());
        String baseMoneySlave = "6259.17";
        createSlavePortfolioWithOutPosition(1, 1, null, baseMoneySlave, positionListSl);
        //отправляем команду на синхронизацию
        createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);

        BigDecimal quantityDiff = BigDecimal.ZERO;
        String tickerPos = "";
        String tradingClearingAccountPos = "";

        ArrayList<BigDecimal> rateList = new ArrayList<>();
        ArrayList<BigDecimal> priceList = new ArrayList<>();
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            rateList.add(slavePortfolio.getPositions().get(i).getRate());
            priceList.add(slavePortfolio.getPositions().get(i).getPrice());
        }

        if (rateList.get(0).compareTo(rateList.get(1)) < 0) {
            quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(0).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(0).getTradingClearingAccount();
        }

        if (rateList.get(1).compareTo(rateList.get(0)) < 0) {
            quantityDiff = slavePortfolio.getPositions().get(1).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(1).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(1).getTradingClearingAccount();
        }

        if (rateList.get(0).compareTo(rateList.get(1)) == 0) {
            if (priceList.get(0).compareTo(priceList.get(1)) < 0) {
                quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
                tickerPos = slavePortfolio.getPositions().get(0).getTicker();
                tradingClearingAccountPos = slavePortfolio.getPositions().get(0).getTradingClearingAccount();
            }
            if (priceList.get(1).compareTo(priceList.get(0)) < 0) {
                quantityDiff = slavePortfolio.getPositions().get(1).getQuantityDiff();
                tickerPos = slavePortfolio.getPositions().get(1).getTicker();
                tradingClearingAccountPos = slavePortfolio.getPositions().get(1).getTradingClearingAccount();
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is("1"));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(lots.multiply(lot)));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(tickerPos));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccountPos));
    }


    @SneakyThrows
    @Test
    @AllureId("695957")
    @DisplayName("695957.SynchronizePositionResolver.Обрабатываем позиции.Несколько позиций, у которых slave_portfolio_position.quantity_diff < 0 и type из exchangePositionCache != 'share'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C695957() {
        String tickerBond1 = "XS0191754729";
        String tradingClearingAccountBond1 = "L01+00000F00";
        String classCodeBond1 = "TQOD";
        String tickerBond2 = "VTBperp";
        String tradingClearingAccountBond2 = "L01+00000SPB";
        String classCodeBond2 = "SPBBND";
        BigDecimal lot = new BigDecimal("1");
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createDataToMarketData(tickerBond1, classCodeBond1, "88.3425", "92.9398", "87.3427");
        createDataToMarketData(tickerBond2, classCodeBond2, "107.2", "108.2", "105.2");

        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
//        создаем команду для топика tracking.event, чтобы очистился кеш contractCache
//        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(tickerBond1)
            .tradingClearingAccount(tradingClearingAccountBond1)
            .quantity(new BigDecimal("10"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(tickerBond2)
            .tradingClearingAccount(tradingClearingAccountBond2)
            .quantity(new BigDecimal("5"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());

        createMasterPortfolio(2, "6259.17", positionListMaster);
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(tickerBond1)
            .tradingClearingAccount(tradingClearingAccountBond1)
            .quantity(new BigDecimal("20"))
            .changedAt(date)
            .build());
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(tickerBond2)
            .tradingClearingAccount(tradingClearingAccountBond2)
            .quantity(new BigDecimal("10"))
            .changedAt(date)
            .build());
        String baseMoneySlave = "6259.17";
        createSlavePortfolioWithOutPosition(1, 1, null, baseMoneySlave, positionListSl);
        //отправляем команду на синхронизацию
        createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);

        BigDecimal quantityDiff = BigDecimal.ZERO;
        String tickerPos = "";
        String tradingClearingAccountPos = "";

        ArrayList<BigDecimal> rateList = new ArrayList<>();
        ArrayList<BigDecimal> priceList = new ArrayList<>();
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            rateList.add(slavePortfolio.getPositions().get(i).getRate());
            priceList.add(slavePortfolio.getPositions().get(i).getPrice());
        }

        if (rateList.get(0).compareTo(rateList.get(1)) < 0) {
            quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(0).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(0).getTradingClearingAccount();
        }


        if (rateList.get(1).compareTo(rateList.get(0)) < 0) {
            quantityDiff = slavePortfolio.getPositions().get(1).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(1).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(1).getTradingClearingAccount();
        }

        if (rateList.get(0).compareTo(rateList.get(1)) == 0) {
            if (priceList.get(0).compareTo(priceList.get(1)) < 0) {
                quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
                tickerPos = slavePortfolio.getPositions().get(0).getTicker();
                tradingClearingAccountPos = slavePortfolio.getPositions().get(0).getTradingClearingAccount();
            }
            if (priceList.get(1).compareTo(priceList.get(0)) < 0) {
                quantityDiff = slavePortfolio.getPositions().get(1).getQuantityDiff();
                tickerPos = slavePortfolio.getPositions().get(1).getTicker();
                tradingClearingAccountPos = slavePortfolio.getPositions().get(1).getTradingClearingAccount();
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is("1"));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(lots.multiply(lot)));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(tickerPos));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccountPos));
    }


    @SneakyThrows
    @Test
    @AllureId("695978")
    @DisplayName("C695978.SynchronizePositionResolver.Обрабатываем позиции. Slave_portfolio_position.quantity_diff > 0 и type из exchangePositionCache IN ('bond', 'etf')")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C695978() {
        String tickerBond = "VTBperp";
        String tradingClearingAccountBond = "L01+00000SPB";
        String classCodeBond = "SPBBND";
        String tickerShare = "QCOM";
        String tradingClearingAccountShare = "L01+00000SPB";
        String classCodeShare = "SPBXM";
        BigDecimal lot = new BigDecimal("1");
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createDataToMarketData(tickerBond, classCodeBond, "107.2", "108.2", "105.2");
        createDataToMarketData(tickerShare, classCodeShare, "55.05", "55.08", "54.82");
        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
//        создаем команду для топика tracking.event, чтобы очистился кеш contractCache
//        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(tickerShare)
            .tradingClearingAccount(tradingClearingAccountShare)
            .quantity(new BigDecimal("20"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(tickerBond)
            .tradingClearingAccount(tradingClearingAccountBond)
            .quantity(new BigDecimal("60"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(2, "6259.17", positionListMaster);
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(tickerShare)
            .tradingClearingAccount(tradingClearingAccountShare)
            .quantity(new BigDecimal("10"))
            .changedAt(date)
            .build());
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(tickerBond)
            .tradingClearingAccount(tradingClearingAccountBond)
            .quantity(new BigDecimal("40"))
            .changedAt(date)
            .build());
        String baseMoneySlave = "6259.17";
        createSlavePortfolioWithOutPosition(1, 1, null, baseMoneySlave, positionListSl);
        //отправляем команду на синхронизацию
        createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = BigDecimal.ZERO;
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            if (tickerBond.equals(slavePortfolio.getPositions().get(i).getTicker())) {
                quantityDiff = slavePortfolio.getPositions().get(i).getQuantityDiff();
                break;
            }
        }
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is("0"));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(lots.multiply(lot)));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(tickerBond));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccountBond));
    }


    @SneakyThrows
    @Test
    @AllureId("695986")
    @DisplayName("695986.SynchronizePositionResolver. Обрабатываем позиции.Несколько позиций, у которых slave_portfolio_position.quantity_diff > 0 и type из exchangePositionCache IN ('bond', 'etf')")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C695986() {
        String tickerBond1 = "XS0191754729";
        String tradingClearingAccountBond1 = "L01+00000F00";
        String classCodeBond1 = "TQOD";
        String tickerBond2 = "VTBperp";
        String tradingClearingAccountBond2 = "L01+00000SPB";
        String classCodeBond2 = "SPBBND";
        BigDecimal lot = new BigDecimal("1");
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createDataToMarketData(tickerBond1, classCodeBond1, "88.3425", "92.9398", "87.3427");
        createDataToMarketData(tickerBond2, classCodeBond2, "107.2", "108.2", "105.2");
        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
//        создаем команду для топика tracking.event, чтобы очистился кеш contractCache
//        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(tickerBond1)
            .tradingClearingAccount(tradingClearingAccountBond1)
            .quantity(new BigDecimal("20"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(tickerBond2)
            .tradingClearingAccount(tradingClearingAccountBond2)
            .quantity(new BigDecimal("12"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());

        createMasterPortfolio(2, "6259.17", positionListMaster);
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(tickerBond1)
            .tradingClearingAccount(tradingClearingAccountBond1)
            .quantity(new BigDecimal("10"))
            .changedAt(date)
            .build());
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(tickerBond2)
            .tradingClearingAccount(tradingClearingAccountBond2)
            .quantity(new BigDecimal("4"))
            .changedAt(date)
            .build());
        String baseMoneySlave = "6259.17";
        createSlavePortfolioWithOutPosition(1, 1, null, baseMoneySlave, positionListSl);
        //отправляем команду на синхронизацию
        createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = BigDecimal.ZERO;
        String tickerPos = "";
        String tradingClearingAccountPos = "";

        ArrayList<BigDecimal> rateList = new ArrayList<>();
        ArrayList<BigDecimal> priceList = new ArrayList<>();
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            rateList.add(slavePortfolio.getPositions().get(i).getRate());
            priceList.add(slavePortfolio.getPositions().get(i).getPrice());
        }

        if (rateList.get(0).compareTo(rateList.get(1)) > 0) {
            quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(0).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(0).getTradingClearingAccount();
        }


        if (rateList.get(1).compareTo(rateList.get(0)) > 0) {
            quantityDiff = slavePortfolio.getPositions().get(1).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(1).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(1).getTradingClearingAccount();
        }

        if (rateList.get(0).compareTo(rateList.get(1)) == 0) {
            if (priceList.get(0).compareTo(priceList.get(1)) > 0) {
                quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
                tickerPos = slavePortfolio.getPositions().get(0).getTicker();
                tradingClearingAccountPos = slavePortfolio.getPositions().get(0).getTradingClearingAccount();
            }
            if (priceList.get(1).compareTo(priceList.get(0)) > 0) {
                quantityDiff = slavePortfolio.getPositions().get(1).getQuantityDiff();
                tickerPos = slavePortfolio.getPositions().get(1).getTicker();
                tradingClearingAccountPos = slavePortfolio.getPositions().get(1).getTradingClearingAccount();
            }
        }
        //получаем значение lot из ExchangePositionCache
//        BigDecimal lot = new BigDecimal(getLotFromExchangePositionCache(tickerPos,  tradingClearingAccountPos));
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is("0"));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(lots.multiply(lot)));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(tickerPos));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccountPos));
    }


    @SneakyThrows
    @Test
    @AllureId("697301")
    @DisplayName("6697301.SynchronizePositionResolver.Обрабатываем позиции. Slave_portfolio_position.quantity_diff > 0 и type = 'share'")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C697301() {
        String tickerShare1 = "ABBV";
        String tradingClearingAccountShare1 = "L01+00000SPB";
        String classCodeShare1 = "SPBXM";
        String tickerShare2 = "QCOM";
        String tradingClearingAccountShare2 = "L01+00000SPB";
        String classCodeShare2 = "SPBXM";
        BigDecimal lot = new BigDecimal("1");
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        createDataToMarketData(tickerShare1, classCodeShare1, "90", "90", "87");
        createDataToMarketData(tickerShare2, classCodeShare2, "55.05", "55.08", "54.82");
        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
//        создаем команду для топика tracking.event, чтобы очистился кеш contractCache
//        createEventInTrackingEvent(contractIdSlave);
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(tickerShare1)
            .tradingClearingAccount(tradingClearingAccountShare1)
            .quantity(new BigDecimal("20"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(tickerShare2)
            .tradingClearingAccount(tradingClearingAccountShare2)
            .quantity(new BigDecimal("12"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        createMasterPortfolio(2, "6259.17", positionListMaster);
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(tickerShare1)
            .tradingClearingAccount(tradingClearingAccountShare1)
            .quantity(new BigDecimal("10"))
            .changedAt(date)
            .build());
        positionListSl.add(SlavePortfolio.Position.builder()
            .ticker(tickerShare2)
            .tradingClearingAccount(tradingClearingAccountShare2)
            .quantity(new BigDecimal("4"))
            .changedAt(date)
            .build());
        String baseMoneySlave = "6259.17";
        createSlavePortfolioWithOutPosition(1, 1, null, baseMoneySlave, positionListSl);
        //отправляем команду на синхронизацию
        createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = BigDecimal.ZERO;
        String tickerPos = "";
        String tradingClearingAccountPos = "";

        ArrayList<BigDecimal> rateList = new ArrayList<>();
        ArrayList<BigDecimal> priceList = new ArrayList<>();
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            rateList.add(slavePortfolio.getPositions().get(i).getRate());
            priceList.add(slavePortfolio.getPositions().get(i).getPrice());
        }

        if (rateList.get(0).compareTo(rateList.get(1)) > 0) {
            quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(0).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(0).getTradingClearingAccount();
        }


        if (rateList.get(1).compareTo(rateList.get(0)) > 0) {
            quantityDiff = slavePortfolio.getPositions().get(1).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(1).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(1).getTradingClearingAccount();
        }

        if (rateList.get(0).compareTo(rateList.get(1)) == 0) {
            if (priceList.get(0).compareTo(priceList.get(1)) > 0) {
                quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
                tickerPos = slavePortfolio.getPositions().get(0).getTicker();
                tradingClearingAccountPos = slavePortfolio.getPositions().get(0).getTradingClearingAccount();
            }
            if (priceList.get(1).compareTo(priceList.get(0)) > 0) {
                quantityDiff = slavePortfolio.getPositions().get(1).getQuantityDiff();
                tickerPos = slavePortfolio.getPositions().get(1).getTicker();
                tradingClearingAccountPos = slavePortfolio.getPositions().get(1).getTradingClearingAccount();
            }
        }
        //получаем значение lot из ExchangePositionCache
//        BigDecimal lot = new BigDecimal(getLotFromExchangePositionCache(tickerPos,  tradingClearingAccountPos));
        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is("0"));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(lots.multiply(lot)));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(tickerPos));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccountPos));
    }


    @SneakyThrows
    @Test
    @AllureId("697225")
    @DisplayName("697225.SynchronizePositionResolver.Обрабатываем позиции.Несколько позиций," +
        " у которых slave_portfolio_position.quantity_diff > 0,первая позиция списка, для покупки которой хватает денег")
    @Subfeature("Успешные сценарии")
    @Description("Алгоритм предназначен для выбора одной позиции для синхронизации портфеля slave'а на основе текущего виртуального master-портфеля")
    void C697225() {
        String tickerShare1 = "ABBV";
        String tradingClearingAccountShare1 = "L01+00000SPB";
        String classCodeShare1 = "SPBXM";
        String tickerShare2 = "QCOM";
        String tradingClearingAccountShare2 = "L01+00000SPB";
        String classCodeShare2 = "SPBXM";
        String title = "тест стратегия autotest update base currency";
        String description = "description test стратегия autotest update adjust base currency";
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        BigDecimal priceAdditional = new BigDecimal("0.002");
        BigDecimal lot = new BigDecimal("1");
        Date date = Date.from(utc.toInstant());
        createDataToMarketData(tickerShare1, classCodeShare1, "90", "90", "87");
        createDataToMarketData(tickerShare2, classCodeShare2, "55.05", "55.08", "54.82");
        //получаем данные по клиенту master в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdMaster = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_MASTER);
        UUID investIdMaster = findValidAccountWithSiebleIdMaster.get(0).getInvestAccount().getId();
        contractIdMaster = findValidAccountWithSiebleIdMaster.get(0).getId();
        //получаем данные по клиенту slave в БД сервиса счетов
        List<BrokerAccount> findValidAccountWithSiebleIdSlave = billingService.getFindValidAccountWithSiebelId(SIEBEL_ID_SLAVE);
        contractIdSlave = findValidAccountWithSiebleIdSlave.get(0).getId();
        strategyId = UUID.randomUUID();
//      создаем в БД tracking данные по Мастеру: client, contract, strategy в статусе active
        createClientWintContractAndStrategy(SIEBEL_ID_MASTER, investIdMaster, contractIdMaster, ContractRole.master, ContractState.untracked,
            strategyId, title, description, StrategyCurrency.usd, StrategyRiskProfile.aggressive,
            StrategyStatus.active, 0, LocalDateTime.now());
        // создаем портфель ведущего с позицией в кассандре
        Tracking.Portfolio.Position positionAction = Tracking.Portfolio.Position.newBuilder()
            .setAction(Tracking.Portfolio.ActionValue.newBuilder()
                .setAction(Tracking.Portfolio.Action.SECURITY_BUY_TRADE).build())
            .build();
        //c позицией по бумаге
        List<MasterPortfolio.Position> positionListMaster = new ArrayList<>();
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(tickerShare1)
            .tradingClearingAccount(tradingClearingAccountShare1)
            .quantity(new BigDecimal("35"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());
        positionListMaster.add(MasterPortfolio.Position.builder()
            .ticker(tickerShare2)
            .tradingClearingAccount(tradingClearingAccountShare2)
            .quantity(new BigDecimal("35"))
            .changedAt(date)
            .lastChangeDetectedVersion(2)
            .lastChangeAction((byte) positionAction.getAction().getActionValue())
            .build());

        createMasterPortfolio(2, "900.5", positionListMaster);
        //создаем запись о ведомом в client
        createSubscriptionSlave(SIEBEL_ID_SLAVE, contractIdSlave, strategyId);
        //создаем портфель для ведомого
        List<SlavePortfolio.Position> positionListSl = new ArrayList<>();
        String baseMoneySlave = "121.3";
        createSlavePortfolioWithOutPosition(1, 1, null, baseMoneySlave, positionListSl);
        //отправляем команду на синхронизацию
        createCommandSynTrackingSlaveCommand(contractIdSlave);
        //получаем портфель slave
        checkComparedToMasterVersion(2);
        //получаем портфель slave
        slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
        BigDecimal quantityDiff = BigDecimal.ZERO;
        String tickerPos = "";
        String tradingClearingAccountPos = "";
        ArrayList<BigDecimal> rateList = new ArrayList<>();
        ArrayList<BigDecimal> priceList = new ArrayList<>();
        ArrayList<BigDecimal> lotsList = new ArrayList<>();
        for (int i = 0; i < slavePortfolio.getPositions().size(); i++) {
            rateList.add(slavePortfolio.getPositions().get(i).getRate());
            priceList.add(slavePortfolio.getPositions().get(i).getPrice());
            lotsList.add(slavePortfolio.getPositions().get(i).getQuantityDiff().abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP)
                .multiply(slavePortfolio.getPositions().get(i).getPrice().add(priceAdditional)));

        }

        if (lotsList.get(0).compareTo(new BigDecimal(baseMoneySlave)) < 0) {
            quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(0).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(0).getTradingClearingAccount();
        }

        if (lotsList.get(1).compareTo(new BigDecimal(baseMoneySlave)) < 0) {
            quantityDiff = slavePortfolio.getPositions().get(0).getQuantityDiff();
            tickerPos = slavePortfolio.getPositions().get(0).getTicker();
            tradingClearingAccountPos = slavePortfolio.getPositions().get(1).getTradingClearingAccount();
        }

        // рассчитываем значение lots
        BigDecimal lots = quantityDiff.abs().divide(lot, 0, BigDecimal.ROUND_HALF_UP);
        slaveOrder = slaveOrderDao.getSlaveOrder(contractIdSlave, strategyId);
        assertThat("Направление заявки Action не равно", slaveOrder.getAction().toString(), is("0"));
        assertThat("Количество бумаг в заявке Quantity не равно", slaveOrder.getQuantity(), is(lots.multiply(lot)));
        assertThat("ticker бумаги не равен", slaveOrder.getTicker(), is(tickerPos));
        assertThat("TradingClearingAccount бумаги не равен", slaveOrder.getTradingClearingAccount(), is(tradingClearingAccountPos));
    }


    // методы для работы тестов*************************************************************************
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

    Tracking.PortfolioCommand createCommandSynchronize(String contractIdSlave) {
        //отправляем команду на синхронизацию
        OffsetDateTime time = OffsetDateTime.now();
        Tracking.PortfolioCommand command = Tracking.PortfolioCommand.newBuilder()
            .setContractId(contractIdSlave)
            .setOperation(Tracking.PortfolioCommand.Operation.SYNCHRONIZE)
            .setCreatedAt(Timestamp.newBuilder()
                .setSeconds(time.toEpochSecond())
                .setNanos(time.getNano())
                .build())
            .build();
        return command;
    }


    //метод отправляет событие с Action = Update, чтобы очистить кеш contractCache
    void createEventInTrackingEvent(String contractIdSlave) throws InterruptedException {
        //создаем событие
        Tracking.Event event = createEventUpdateAfterSubscriptionSlave(contractIdSlave);
        log.info("Команда в tracking.event:  {}", event);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = event.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send("tracking.event", contractIdSlave, eventBytes);
    }


    //метод отправляет команду с operation = 'SYNCHRONIZE'.
    void createCommandSynTrackingSlaveCommand(String contractIdSlave) {
        //создаем команду
        Tracking.PortfolioCommand command = createCommandSynchronize(contractIdSlave);
        log.info("Команда в tracking.slave.command:  {}", command);
        //кодируем событие по protobuf схеме и переводим в byteArray
        byte[] eventBytes = command.toByteArray();
        //отправляем событие в топик kafka tracking.slave.command
        kafkaSender.send("tracking.slave.command", contractIdSlave, eventBytes);
    }


    //метод создает клиента, договор и стратегию в БД автоследования
    void createClientWintContractAndStrategy(String SIEBLE_ID, UUID investId, String contractId, ContractRole contractRole, ContractState contractState,
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


    //вызываем метод CreateSubscription для slave
    void createSubscriptionSlave(String siebleIdSlave, String contractIdSlave, UUID strategyId) {
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


    void createMasterPortfolio(int version, String money, List<MasterPortfolio.Position> positionList) {
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

    //создаем портфель master в cassandra
    void createSlavePortfolioWithOutPosition(int version, int comparedToMasterVersion, String currency, String money,
                                             List<SlavePortfolio.Position> positionList) {
        //с базовой валютой
        OffsetDateTime utc = OffsetDateTime.now(ZoneOffset.UTC);
        Date date = Date.from(utc.toInstant());
        SlavePortfolio.BaseMoneyPosition baseMoneyPosition = SlavePortfolio.BaseMoneyPosition.builder()
            .quantity(new BigDecimal(money))
            .changedAt(date)
            .build();
        //insert запись в cassandra
        slavePortfolioDao.insertIntoSlavePortfolioWithChangedAt(contractIdSlave, strategyId, version, comparedToMasterVersion,
            baseMoneyPosition, positionList, date);
    }


    public void checkPositionParameters(int pos, String ticker, String tradingClearingAccount, String quantityPos,
                                        Integer synchronizedToMasterVersion, BigDecimal price, BigDecimal slavePositionRate,
                                        BigDecimal masterPositionRate, BigDecimal quantityDiff) {
        assertThat("ticker бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTicker(), is(ticker));
        assertThat("tradingClearingAccount  бумаги позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getTradingClearingAccount(), is(tradingClearingAccount));
        assertThat("Quantity позиции в портфеле slave не равна", slavePortfolio.getPositions().get(pos).getQuantity().toString(), is(quantityPos));
        assertThat("SynchronizedToMasterVersion позиции в портфеле slave не равна", slavePortfolio.getPositions().get(0).getSynchronizedToMasterVersion(), is(synchronizedToMasterVersion));
        assertThat("Price позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getPrice(), is(price));
        assertThat("Rate позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRate(), is(slavePositionRate));
        assertThat("RateDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getRateDiff(), is(masterPositionRate));
        assertThat("QuantityDiff позиции в портфеле slave не равен", slavePortfolio.getPositions().get(pos).getQuantityDiff(), is(quantityDiff));

    }

    // отправляем событие в tracking.test.md.prices.int.stream
    public void createEventTrackingTestMdPricesInStream(String instrumentId, String type, String oldPrice, String newPrice) {
        String event = PriceUpdatedEvent.getKafkaTemplate(LocalDateTime.now(ZoneOffset.UTC), instrumentId, type, oldPrice, newPrice);
        String key = PriceUpdatedKey.getKafkaTemplate(instrumentId);
        //отправляем событие в топик kafka tracking.test.md.prices.int.stream
        stringSenderService.send(Topics.TRACKING_TEST_MD_PRICES_INT_STREAM, key, event);
    }

    String getPriceFromMarketData(String instrumentId, String type, String priceForTest) {
        Response res = pricesApi.mdInstrumentPrices()
            .instrumentIdPath(instrumentId)
            .requestIdQuery("111")
            .systemCodeQuery("111")
            .typesQuery(type)
            .respSpec(spec -> spec.expectStatusCode(200))
            .execute(response -> response);
        String price = res.getBody().jsonPath().getString("prices.price_value[0]");

        if (price == null) {
            price = priceForTest;
        }
        return price;
    }

    void createDataToMarketData(String ticker, String classCode, String lastPrice, String askPrice, String bidPrice) {
        //получаем данные от маркет даты по ценам: last, ask, bid  и кидаем их в тестовый топик
        String last = getPriceFromMarketData(ticker + "_" + classCode, "last", lastPrice);
        String ask = getPriceFromMarketData(ticker + "_" + classCode, "ask", askPrice);
        String bid = getPriceFromMarketData(ticker + "_" + classCode, "bid", bidPrice);
        createEventTrackingTestMdPricesInStream(ticker + "_" + classCode, "last", lastPrice, last);
        createEventTrackingTestMdPricesInStream(ticker + "_" + classCode, "ask", askPrice, ask);
        createEventTrackingTestMdPricesInStream(ticker + "_" + classCode, "bid", bidPrice, bid);
    }

    void checkComparedToMasterVersion(int version) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            slavePortfolio = slavePortfolioDao.getLatestSlavePortfolio(contractIdSlave, strategyId);
            if (slavePortfolio.getComparedToMasterVersion() != version) {
                Thread.sleep(5000);
            }
        }
    }

}
