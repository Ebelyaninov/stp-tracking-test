package ru.qa.tinkoff.mocks.steps.fireg;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.mocks.model.TextResourceEnhancer;
import ru.qa.tinkoff.mocks.model.fireg.GetDividendAAPLTemplateEnhancer;
import ru.qa.tinkoff.mocks.model.fireg.GetDividendForOneElementTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static io.restassured.RestAssured.given;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetDividendsSteps {
    public static final String baseUrlForCreate = "http://invest-automation-mockserver.invest-automation-mockserver.v2.dev2.k8s.tcsbank.ru/mockserver/expectation";
    public static final String baseUrlForClear = "http://invest-automation-mockserver.invest-automation-mockserver.v2.dev2.k8s.tcsbank.ru/mockserver/clear?type=all";

    //метод для очистки моков
    public void clearGetDevidends () {
        String path =  "/v1/fireg/instruments/.*/dividends";
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

    //Создаем мок, для getDividends
    public void createGetDividends (String body){
        given()
            .log()
            .all()
            .header("Accept", "*/*")
            .body(body)
            .when().put(baseUrlForCreate)
            .then().statusCode(201);
    }

    public String createBodyForAAPL (String dividendNet, String paymentDate, String lastBuyDate){
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String paymentDatePlusDay = date.plusDays(1).format(formatter) + "T03:00:00+03:00";
        String paymentDateMinusEightDays = date.minusDays(7).format(formatter) + "T03:00:00+03:00";
        String body = TextResourceEnhancer.enhance(
            new GetDividendAAPLTemplateEnhancer(paymentDatePlusDay, dividendNet, paymentDate, lastBuyDate, paymentDateMinusEightDays));
        return  body;
    }

    public String createBodyForGetDividendWithOneElement (String ticker, String classCode, String dividendId, String instrumentId, String dividendNet, String dividendCurrency, String paymentDate, String lastBuyDate, String status){
        String body = TextResourceEnhancer.enhance(
            new GetDividendForOneElementTemplate(ticker, classCode, dividendId, instrumentId, dividendNet, dividendCurrency, paymentDate, lastBuyDate, status));
        return  body;
    }

}
