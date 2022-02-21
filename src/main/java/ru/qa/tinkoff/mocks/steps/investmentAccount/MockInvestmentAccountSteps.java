package ru.qa.tinkoff.mocks.steps.investmentAccount;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.mocks.model.InvestmentAccount.GetBrokerAccountBySiebelTemplateEnhancer;
import ru.qa.tinkoff.mocks.model.InvestmentAccount.GetInvestIdTemplateEnhancer;
import ru.qa.tinkoff.mocks.model.TextResourceEnhancer;

import static io.restassured.RestAssured.given;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockInvestmentAccountSteps {

    public static final String baseUrlForCreate = "http://invest-automation-mockserver.dev2.k8s.tcsbank.ru/mockserver/expectation";
    public static final String baseUrlForClear = "http://invest-automation-mockserver.dev2.k8s.tcsbank.ru/mockserver/clear?type=all";


    //метод для очистки моков
    public void clearMocks (String path) {
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

    public void createRestMock (String body){
        given()
            .log()
            .all()
            .header("Accept", "*/*")
            .body(body)
            .when().put(baseUrlForCreate)
            .then().statusCode(201);
    }

    public String createBodyForGetInvestId (String path,  String investId){
        String body = TextResourceEnhancer.enhance(
            new GetInvestIdTemplateEnhancer(path, investId));
        return body;
    }

    public String createBodyForGetBrokerAccountBySiebel(String investId, String siebelId, String contratId){
        String path  = "/account/public/v1/broker-account/siebel/" + siebelId;
        String body = TextResourceEnhancer.enhance(
            new GetBrokerAccountBySiebelTemplateEnhancer(path, investId, contratId));
        return body;
    }

}
