package ru.qa.tinkoff.mocks.steps.middle;

import com.google.api.client.testing.util.SecurityTestUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.mocks.model.TextResourceEnhancer;
import ru.qa.tinkoff.mocks.model.middle.*;

import static io.restassured.RestAssured.given;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockMiddleSteps {

    public static final String urlForGrpc = "http://midle-pos-grpc-mock.invest-autoqa-ns.v2.dev2.k8s.tcsbank.ru/add";
    public static final String urlForClean = "http://midle-pos-grpc-mock.invest-autoqa-ns.v2.dev2.k8s.tcsbank.ru/clear";
    public static final String urlForRestOrder = "http://invest-automation-mockserver.invest-automation-mockserver.v2.dev2.k8s.tcsbank.ru/mockserver/expectation";
    public static final String urlForRestOrderClean = "http://invest-automation-mockserver.invest-automation-mockserver.v2.dev2.k8s.tcsbank.ru/mockserver/clear?type=all";

    //метод для очистки моков GRPC
    public void clearMocksForGrpc () {
        given()
            .log()
            .all()
            .header("Accept", "*/*")
            .when().get(urlForClean)
            .then().statusCode(200);
    }

    //метод для очистки моков rest
    public void clearMocksForRestOrder () {
        String body = createPathBody("/api/miof/order/execute");
        given()
            .log()
            .all()
            .header("Accept", "*/*")
            .body(body)
            .when().put(urlForRestOrderClean)
            .then().statusCode(200);
    }

    //  шаблон Json объектов для POST запроса для метода clean
    @SneakyThrows
    static String createPathBody (String path) {
        JSONObject createBodyJSON = new JSONObject();
        createBodyJSON.put("path", path);
        return createBodyJSON.toString();
    }

    public String createBodyForGrpc (String agreementId, String eurUnscaledPrice, String rubUnscaledPrice, String usdUnscaledPrice, String usdScaledQty, String quantityAAPL, String ticker, String tradingAccont){
        String body = TextResourceEnhancer.enhance(
            new MiddleGRPCMethodEnhancer(agreementId, eurUnscaledPrice, rubUnscaledPrice, usdUnscaledPrice, usdScaledQty, quantityAAPL, ticker, tradingAccont));
        return body;
    }

    public String createBodyForGrpcOne (String agreementId, String usdUnscaledPrice, String usdScaledQty, String quantity, String ticker, String tradingAccount, String quantityCCL, String tickerCCL, String tradingAccountCCL){
        String body = TextResourceEnhancer.enhance(
            new MiddleGRPCMethodEnhancerOne(agreementId, usdUnscaledPrice, usdScaledQty, quantity, ticker, tradingAccount, quantityCCL, tickerCCL, tradingAccountCCL));
        return body;
    }

    public String createBodyForGrpcTwo (String agreementId, String usdUnscaledPrice, String usdScaledQty, String quantity, String ticker, String tradingAccount){
        String body = TextResourceEnhancer.enhance(
            new MiddleGRPCMethodEnhancerTwo(agreementId, usdUnscaledPrice, usdScaledQty, quantity, ticker, tradingAccount));
        return body;
    }

    public void createGrpcMock (String body){
        given()
            .log()
            .all()
            .header("Accept", "*/*")
            .body(body)
            .when().post(urlForGrpc)
            .then().statusCode(200);
    }

    public String createBodyForRestOrder (String ticker, String action, String contractId, String classCode, String timeInForce,
                                          String executionReportStatus, String lotsRequested, String lotsExecuted, String clientCode){
        String body = TextResourceEnhancer.enhance(
            new MiddleRestOrderEnhancer(ticker, action, contractId, classCode, timeInForce, executionReportStatus, lotsRequested, lotsExecuted, clientCode));
        return body;
    }


    public void createRestOrder (String body){
        given()
            .log()
            .all()
            .header("Accept", "*/*")
            .body(body)
            .when().put(urlForRestOrder)
            .then().statusCode(201);
    }


    public String createBodyForRestOrderError (String ticker, String action, String contractId, String classCode, String timeInForce,
                                          String message, String code, String clientCode){
        String body = TextResourceEnhancer.enhance(
            new MiddleRestOrderErrorEnhancer(ticker, action, contractId, classCode, timeInForce, message, code, clientCode));
        return body;
    }


    public void createRestOrderError (String body){
        given()
            .log()
            .all()
            .header("Accept", "*/*")
            .body(body)
            .when().put(urlForRestOrder)
            .then().statusCode(201);
    }



}
