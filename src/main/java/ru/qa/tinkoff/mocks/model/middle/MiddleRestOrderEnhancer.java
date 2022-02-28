package ru.qa.tinkoff.mocks.model.middle;

import lombok.Builder;
import ru.qa.tinkoff.mocks.model.TextResourceInfo;

import java.util.Map;

@Builder
public class MiddleRestOrderEnhancer implements TextResourceInfo {

    private final String ticker;
    private final String action;
    private final String contractId;
    private final String classCode;
    private final String timeInForce;
    private final String executionReportStatus;
    private final String lotsRequested;
    private final String lotsExecuted;
    private final String clientCode;

    public MiddleRestOrderEnhancer (String ticker, String action, String contractId, String classCode, String timeInForce,
                                    String executionReportStatus, String lotsRequested, String lotsExecuted, String clientCode) {
        this.ticker = ticker;
        this.action = action;
        this.contractId = contractId;
        this.classCode = classCode;
        this.timeInForce = timeInForce;
        this.executionReportStatus = executionReportStatus;
        this.lotsRequested = lotsRequested;
        this.lotsExecuted = lotsExecuted;
        this.clientCode = clientCode;
    }

    @Override
    public String path() {
        return "mockTemplate/middleRestOrderTemplate";
    }

    @Override
    public Map<String, String> params() {
        return Map.of("ticker", ticker, "action", action, "contractId", contractId, "classCode", classCode, "timeInForce", timeInForce, "executionReportStatus", executionReportStatus, "lotsRequested", lotsRequested, "lotsExecuted", lotsExecuted,"clientCode", clientCode);

    }
}
