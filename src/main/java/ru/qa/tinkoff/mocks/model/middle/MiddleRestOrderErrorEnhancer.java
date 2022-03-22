package ru.qa.tinkoff.mocks.model.middle;

import ru.qa.tinkoff.mocks.model.TextResourceInfo;

import java.util.Map;

public class MiddleRestOrderErrorEnhancer implements TextResourceInfo {

    private final String ticker;
    private final String action;
    private final String contractId;
    private final String classCode;
    private final String timeInForce;
    private final String message;
    private final String code;
    private final String clientCode;

    public MiddleRestOrderErrorEnhancer (String ticker, String action, String contractId, String classCode, String timeInForce,
                                    String message, String code, String clientCode) {
        this.ticker = ticker;
        this.action = action;
        this.contractId = contractId;
        this.classCode = classCode;
        this.timeInForce = timeInForce;
        this.message = message;
        this.code = code;
        this.clientCode = clientCode;
    }

    @Override
    public String path() {
        return "mockTemplate/middleRestOrderErrorTemplate";
    }

    @Override
    public Map<String, String> params() {
        return Map.of("ticker", ticker, "action", action, "contractId", contractId, "classCode", classCode, "timeInForce", timeInForce, "message", message, "code", code, "clientCode", clientCode);

    }
}
