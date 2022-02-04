package ru.qa.tinkoff.mocks.model.InvestmentAccount;

import lombok.Builder;
import ru.qa.tinkoff.mocks.model.TextResourceInfo;

import java.util.Map;

@Builder
public class GetBrokerAccountBySiebelTemplateEnhancer implements TextResourceInfo {

    private final String path;
    private final String investId;
    private final String contractId;

    public GetBrokerAccountBySiebelTemplateEnhancer(String path, String investId, String contractId) {
        this.path = path;
        this.investId = investId;
        this.contractId = contractId;
    }

    @Override
    public String path() {
        return "mockTemplate/getBrokerAccountBySiebelTemplate";
    }

    @Override
    public Map<String, String> params() {
        return Map.of("path", path, "investId", investId, "contractId", contractId);
    }
}
