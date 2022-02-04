package ru.qa.tinkoff.mocks.model.InvestmentAccount;

import lombok.Builder;
import ru.qa.tinkoff.mocks.model.TextResourceInfo;

import java.util.Map;

@Builder
public class GetInvestIdTemplateEnhancer implements TextResourceInfo {

    private final String path;
    private final String investId;

    public GetInvestIdTemplateEnhancer(String path, String investId) {
        this.path = path;
        this.investId = investId;
    }

    @Override
    public String path() {
        return "mockTemplate/getInvestIdTemplate";
    }

    @Override
    public Map<String, String> params() {
        return Map.of("path", path, "investId", investId);
    }
}
