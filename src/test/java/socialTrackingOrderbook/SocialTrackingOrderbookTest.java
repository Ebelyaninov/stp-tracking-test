package socialTrackingOrderbook;

import extenstions.RestAssuredExtension;
import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.junit5.AllureJunit5;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.creator.ApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.AdminApiCreatorConfiguration;
import ru.qa.tinkoff.creator.adminCreator.StrategyApiAdminCreator;
import ru.qa.tinkoff.investTracking.configuration.InvestTrackingAutoConfiguration;
import ru.qa.tinkoff.investTracking.entities.Orderbook;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioDao;
import ru.qa.tinkoff.investTracking.services.MasterPortfolioValueDao;
import ru.qa.tinkoff.investTracking.services.OrderbookDao;
import ru.qa.tinkoff.kafka.configuration.KafkaAutoConfiguration;
import ru.qa.tinkoff.kafka.configuration.KafkaOldConfiguration;
import ru.qa.tinkoff.kafka.oldkafkaservice.OldKafkaReceiverService;
import ru.qa.tinkoff.kafka.oldkafkaservice.OldKafkaService;
import ru.qa.tinkoff.kafka.services.ByteArrayReceiverService;
import ru.qa.tinkoff.social.configuration.SocialDataBaseAutoConfiguration;
import ru.qa.tinkoff.social.services.database.ProfileService;
import ru.qa.tinkoff.steps.StpTrackingInstrumentConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSiebelConfiguration;
import ru.qa.tinkoff.steps.StpTrackingSlaveStepsConfiguration;
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingSiebel.StpSiebel;
import ru.qa.tinkoff.steps.trackingSlaveSteps.StpTrackingSlaveSteps;
import ru.qa.tinkoff.tracking.configuration.TrackingDatabaseAutoConfiguration;
import ru.qa.tinkoff.tracking.services.database.ClientService;
import ru.qa.tinkoff.tracking.services.database.ContractService;
import ru.qa.tinkoff.tracking.services.database.StrategyService;
import ru.qa.tinkoff.tracking.services.database.TrackingService;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static ru.qa.tinkoff.kafka.Topics.MD_MOEX_PROTO_OB_FULL_STREAM;
import static ru.qa.tinkoff.kafka.Topics.MD_RTS_PROTO_OB_FULL_STREAM;

@Slf4j
@ExtendWith({AllureJunit5.class, RestAssuredExtension.class})
@Epic("handle[Rts/Moex]OrderbookEvent - Обработка событий по изменению стакана инструмента")

@DisplayName("social-tracking-orderbook")
@Tags({@Tag("social-tracking-orderbook"), @Tag("handle[Rts/Moex]OrderbookEvent")})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = {
    TrackingDatabaseAutoConfiguration.class,
    SocialDataBaseAutoConfiguration.class,
    StpTrackingSlaveStepsConfiguration.class,
    StpTrackingSiebelConfiguration.class,
    InvestTrackingAutoConfiguration.class,
    AdminApiCreatorConfiguration.class,
    ApiCreatorConfiguration.class,
    StpTrackingInstrumentConfiguration.class,
    KafkaAutoConfiguration.class,
    KafkaOldConfiguration.class,
})
public class SocialTrackingOrderbookTest {
    @Autowired
    ByteArrayReceiverService kafkaReceiver;
    @Autowired
    ClientService clientService;
    @Autowired
    ContractService contractService;
    @Autowired
    StrategyService strategyService;
    @Autowired
    ProfileService profileService;
    @Autowired
    TrackingService trackingService;
    @Autowired
    StpTrackingSlaveSteps steps;
    @Autowired
    MasterPortfolioDao masterPortfolioDao;
    @Autowired
    StpSiebel siebel;
    @Autowired
    StrategyApiAdminCreator strategyApiStrategyApiAdminCreator;
    @Autowired
    MasterPortfolioValueDao masterPortfolioValueDao;
    @Autowired
    OldKafkaService oldKafkaService;
    @Autowired
    OldKafkaReceiverService oldKafkaReceiverService;
    @Autowired
    StpInstrument instrument;
    @Autowired
    OrderbookDao orderbookDao;
    Orderbook orderbook;

    List<ru.qa.tinkoff.swagger.trackingSlaveCache.model.Entity> cache;


    @BeforeAll
    public void getData() {
        //получаем содержимое кеш exchangeInstrumentIdCache
        cache = steps.exchangeInstrumentIdCache();

    }


    @Test
    @AllureId("1805796")
    @DisplayName("C1805796.handleMoexOrderbookEven.Проверка агрегации полученные изменения стакана и сохранение значений в БД по операции handleMoexOrderbookEvent")
    @Description("Метод для администратора для активации (публикации) стратегии.")
    void C1805796() throws Exception {
        String ticker = "";
        String tradingClearingAccount = "";
        String instrumentId = "";
        //случаем топик MD_MOEX_PROTO_OB_FULL_STREAM 1 мин и проверяем полученные события: в кеш ExchangeInstrumentIdCache и TrackingExchangePositionCache
        //Смотрим, сообщение, которое поймали в топике kafka
        List<Pair<String, byte[]>> messagesFromMD = oldKafkaReceiverService.
            receiveBatch(MD_MOEX_PROTO_OB_FULL_STREAM, Duration.ofSeconds(30));
        for (int i = 0; i < messagesFromMD.size(); i++) {
            //получаем ключ события
            instrumentId = messagesFromMD.get(i).getKey();
            //проверяем есть ли инструмент по ключу в кешах exchangeInstrumentIdCache
            Map<String, String> exchangePositionId = steps.getDateFromInstrumentCache(cache, instrumentId);
            //достаем значения ticker и tradingClearingAccount
            ticker = exchangePositionId.get("ticker");
            tradingClearingAccount = exchangePositionId.get("tradingClearingAccount");
            //сли нашил инстру
            boolean trackingAllowedValue = steps.getTrackingExchangePositionCache(ticker, tradingClearingAccount);
            if (trackingAllowedValue == true) {
                //находим инструмент, который есть в кешах и проверяем, что по нему есть записи в табл.orderbook за
                // текущую дату
                String finalInstrumentId = instrumentId;
                com.datastax.driver.core.LocalDate now = com.datastax.driver.core.LocalDate.fromMillisSinceEpoch(new Date().getTime());
                List<Orderbook> orderbook = orderbookDao.findWindowsInstrumentIdEndedAtDate(finalInstrumentId, now);
                if (!orderbook.isEmpty()) {
                    assertThat("идентификатор инструмента не равно", orderbook.get(0).getInstrumentId(), is(finalInstrumentId));
                    Date endAtDate = orderbook.get(0).getEndedAt();
                    Date startAtDate = orderbook.get(0).getStartedAt();
                    Date endAtDateOld = orderbook.get(1).getEndedAt();
                    long millisecondsWindow = endAtDate.getTime() - startAtDate.getTime();
                    long millisecondsJump = endAtDate.getTime() - endAtDateOld.getTime();
                    int secondsWindow = (int) (millisecondsWindow / (1000));
                    int secondsump = (int) (millisecondsJump / (1000));
                    assertThat("время окна не равно", secondsWindow, is(60));
                    assertThat("время прыжка не равно", secondsump, is(10));
                    break;
                }
                if (trackingAllowedValue == false) {
                    log.info("не нашли подходящий инструмент");
                }
            }
        }
    }

    @Test
    @AllureId("1800627")
    @DisplayName("C1800627.handleRtsOrderbookEven.Проверка агрегации полученные изменения стакана и сохранение значений в БД по операции handleRtsOrderbookEvent")
    @Description("Метод для администратора для активации (публикации) стратегии.")
    void C1800627() throws Exception {
        String ticker = "";
        String tradingClearingAccount = "";
        String instrumentId = "";
        //случаем топик MD_RTS_PROTO_OB_FULL_STREAM 1 мин и проверяем полученные события: в кеш ExchangeInstrumentIdCache и TrackingExchangePositionCache
        List<Pair<String, byte[]>> messagesFromMD = oldKafkaReceiverService.
            receiveBatch(MD_RTS_PROTO_OB_FULL_STREAM, Duration.ofSeconds(30));
        for (int i = 0; i < messagesFromMD.size(); i++) {
            //получаем ключ события
            instrumentId = messagesFromMD.get(i).getKey();
            //проверяем есть ли инструмент по ключу в кешах
            Map<String, String> exchangePositionId = steps.getDateFromInstrumentCache(cache, instrumentId);
            ticker = exchangePositionId.get("ticker");
            tradingClearingAccount = exchangePositionId.get("tradingClearingAccount");
            boolean trackingAllowedValue = steps.getTrackingExchangePositionCache(ticker, tradingClearingAccount);
            if (trackingAllowedValue == true) {
                //находим инструмент, который есть в кешах и проверяем, что по нему есть записи в табл.orderbook за
                // текущую дату
                String finalInstrumentId = instrumentId;
                com.datastax.driver.core.LocalDate now = com.datastax.driver.core.LocalDate.fromMillisSinceEpoch(new Date().getTime());
                List<Orderbook> orderbook = orderbookDao.findWindowsInstrumentIdEndedAtDate(finalInstrumentId, now);
                if (!orderbook.isEmpty()) {
                    assertThat("идентификатор инструмента не равно", orderbook.get(0).getInstrumentId(), is(finalInstrumentId));
                    Date endAtDate = orderbook.get(0).getEndedAt();
                    Date startAtDate = orderbook.get(0).getStartedAt();
                    Date endAtDateOld = orderbook.get(1).getEndedAt();
                    long millisecondsWindow = endAtDate.getTime() - startAtDate.getTime();
                    long millisecondsJump = endAtDate.getTime() - endAtDateOld.getTime();
                    int secondsWindow = (int) (millisecondsWindow / (1000));
                    int secondsump = (int) (millisecondsJump / (1000));
                    assertThat("время окна не равно", secondsWindow, is(60));
                    assertThat("время прыжка не равно", secondsump, is(10));
                    break;
                }
                if (trackingAllowedValue == false) {
                    log.info("не нашли подходящий инструмент");
                }
            }
        }
    }
}
