package ru.qa.tinkoff.mocks.steps.fireg;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.mocks.model.TextResourceEnhancer;
import ru.qa.tinkoff.mocks.model.fireg.TradingShedulesExchangeSetTimeTemplate;
import ru.qa.tinkoff.mocks.model.fireg.TradingShedulesExchangeTemplateEnhancer;
import ru.qa.tinkoff.mocks.model.fireg.TradingShedulesExchangeTemplateFX;
import ru.qa.tinkoff.swagger.fireg.model.TradingScheduleExchange;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static io.restassured.RestAssured.given;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingShedulesExchangeSteps {

    public static final String baseUrlForCreate = "http://invest-automation-mockserver.dev2.k8s.tcsbank.ru/mockserver/expectation";
    public static final String baseUrlForClear = "http://invest-automation-mockserver.dev2.k8s.tcsbank.ru/mockserver/clear?type=all";

    //метод для очистки моков
    public void clearTradingShedulesExchange () {
        String path =  "/v1/fireg/trading-schedules/.*";
        String body = createPathBody(path);
        given()
            .log()
            .all()
            .header("Accept", "*/*")
            .body(body)
            .when().put(baseUrlForClear)
            .then().statusCode(200);
    }

    //  шаблон Json объектов для POST запроса для метода clean
    @SneakyThrows
    static String createPathBody (String path) {
        JSONObject createBodyJSON = new JSONObject();
        createBodyJSON.put("path", path);
        return createBodyJSON.toString();
    }

    //SPB_MORNING_WEEKEND
    public void createTradingShedulesExchange (String body){
        //TradingScheduleExchange test = getTradingShedulesExchange("2022-02-07", "2022-02-17", "SPB_MORNING_WEEKEND");
        given()
            .log()
            .all()
            .header("Accept", "*/*")
            .body(body)
            .when().put(baseUrlForCreate)
            .then().statusCode(201);
    }


    public String createBodyForTradingShedulesExchange (String exchange){
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String currentDate = date.format(formatter);
        String currentDatePlusOne = date.plusDays(1).format(formatter);
        String currentDatePlusTwo = date.plusDays(2).format(formatter);
        String body = TextResourceEnhancer.enhance(
            new TradingShedulesExchangeTemplateEnhancer (exchange, currentDate, currentDatePlusOne, currentDatePlusTwo));
        return  body;
    }

    public String createBodyForTradingShedulesExchangeFX (String exchange){
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String currentDate = date.format(formatter);
        String currentDatePlusOne = date.plusDays(1).format(formatter);
        String currentDatePlusTwo = date.plusDays(2).format(formatter);
        String body = TextResourceEnhancer.enhance(
            new TradingShedulesExchangeTemplateFX(exchange, currentDate, currentDatePlusOne, currentDatePlusTwo));
        return  body;
    }

    public String createBodyForTradingShedulesExchangeSetTime (String exchange){
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String currentDate = date.format(formatter);
        String currentDatePlusOne = date.plusDays(1).format(formatter);
        String currentDatePlusTwo = date.plusDays(2).format(formatter);

        LocalDateTime start = LocalDateTime.now().plusSeconds(20);
        DateTimeFormatter formatterTime = DateTimeFormatter.ofPattern("HH:mm:ss");
        String startTimeNow = start.format(formatterTime);

        String body = TextResourceEnhancer.enhance(
            new TradingShedulesExchangeSetTimeTemplate(startTimeNow, exchange, currentDate, currentDatePlusOne, currentDatePlusTwo));
        return  body;
    }


    public TradingScheduleExchange getTradingShedulesExchange (String startDate, String endDate, String exchange){
        TradingScheduleExchange TradingShedulesExchange =
            given()
                .log()
                .all()
                .header("Accept", "application/json")
                .queryParam("start_date", startDate)
                .queryParam("end_date", endDate)
                .when().get("http://trading-test.tinkoff.ru/v1/fireg/trading-schedules/" + exchange)
                .then().statusCode(200)
                .extract().response().as(TradingScheduleExchange.class);
        return TradingShedulesExchange;
    }
}
