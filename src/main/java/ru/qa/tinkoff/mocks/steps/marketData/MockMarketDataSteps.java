package ru.qa.tinkoff.mocks.steps.marketData;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.mocks.model.TextResourceEnhancer;
import ru.qa.tinkoff.mocks.model.marketData.MarketDataInstrumentPricesTemplateEnhancer;

import static io.restassured.RestAssured.given;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockMarketDataSteps {

    public static final String url = "http://invest-automation-mockserver.invest-automation-mockserver.v2.dev2.k8s.tcsbank.ru/mockserver/expectation";
    public static final String urlForClean = "http://invest-automation-mockserver.invest-automation-mockserver.v2.dev2.k8s.tcsbank.ru/mockserver/clear?type=all";

    //метод для очистки моков
    public void clearMocks (String tickerAndClassCode) {
        String path = "/v1/md/instruments/" +  tickerAndClassCode + "/prices$";
        String body = createPathBody(path);
        given()
            .log()
            .all()
            .header("Accept", "*/*")
            .body(body)
            .when().put(urlForClean)
            .then().statusCode(200);
    }

    //  шаблон Json объектов для POST запроса для метода clean
    @SneakyThrows
    static String createPathBody (String path) {
        JSONObject createBodyJSON = new JSONObject();
        createBodyJSON.put("path", path);
        return createBodyJSON.toString();
    }

    public void createRestMock (String body){
        //String path = "/v1/md/instruments/" + tickerAndClassCode + "/prices";
        given()
            .log()
            .all()
            .header("Accept", "*/*")
            .body(body)
            .when().put(url)
            .then().statusCode(201);
    }

    public String createBodyForInstrumentPrices(String tickerAndClassCode, String type, String ts, String priceValue){
        String path = "/v1/md/instruments/" + tickerAndClassCode + "/prices";
        String body = TextResourceEnhancer.enhance(
            new MarketDataInstrumentPricesTemplateEnhancer(path, type, tickerAndClassCode, ts, priceValue));
        return  body;
    }
}
