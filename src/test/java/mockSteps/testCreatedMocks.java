package mockSteps;

import io.qameta.allure.AllureId;
import io.qameta.allure.Description;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.qa.tinkoff.allure.Subfeature;
import ru.qa.tinkoff.investTracking.entities.MasterPortfolio;
import ru.qa.tinkoff.investTracking.entities.SlaveOrder;
import ru.qa.tinkoff.investTracking.entities.SlavePortfolio;
import ru.qa.tinkoff.mocks.steps.investmentAccount.MockInvestmentAccountSteps;
import ru.qa.tinkoff.mocks.steps.investmentAccount.MockInvestmentAccountStepsConfiguration;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.Contract;
import ru.qa.tinkoff.tracking.entities.Subscription;

import java.util.UUID;

@SpringBootTest(classes = {
    MockInvestmentAccountStepsConfiguration.class

})
public class testCreatedMocks {

    @Autowired
    MockInvestmentAccountSteps mockInvestmentAccountSteps;

    MasterPortfolio masterPortfolio;
    SlavePortfolio slavePortfolio;
    SlaveOrder slaveOrder;
    Contract contract;
    Client clientSlave;
    String contractIdMaster;
    Subscription subscription;


    String ticker = "AAPL";
    String tradingClearingAccount = "TKCBM_TCAB";
    String classCode = "SPBXM";

    String tickerABBV = "ABBV";
    String classCodeABBV = "SPBXM";
    String tradingClearingAccountABBV = "TKCBM_TCAB";

    String tickerYNDX = "YNDX";
    //    String tradingClearingAccountYNDX = "L01+00000F00";
    String tradingClearingAccountYNDX = "Y02+00001F00";
    String classCodeYNDX = "TQBR";

    String tickerSBER = "SBER";
    String tradingClearingAccountSBER = "L01+00002F00";
    String classCodeSBER = "TQBR";

    String tickerUSD = "USDRUB";
    String tradingClearingAccountUSD = "MB9885503216";
    //    String tradingClearingAccountUSD = "MB0253214128";
    String classCodeUSD = "EES_CETS";

    String tickerGBP = "GBPRUB";
    String tradingClearingAccountGBP = "MB9885503216";
    //    String tradingClearingAccountGBP = "MB0253214128";
    String classCodeGBP = "EES_CETS";

    String tickerCHF = "CHFRUB";
    String tradingClearingAccountCHF = "MB9885503216";
    //    String tradingClearingAccountCHF = "MB0253214128";
    String classCodeCHF = "EES_CETS";

    String tickerHKD = "HKDRUB";
    String tradingClearingAccountHKD = "MB9885503216";
    //    String tradingClearingAccountHKD = "MB0253214128";
    String classCodeHKD = "EES_CETS";

    String tickerFB = "FB";
    String classCodeFB = "SPBXM";
    String tradingClearingAccountFB = "TKCBM_TCAB";
//   String tradingClearingAccountFB = "L01+00000SPB";

    String contractIdSlave;
    UUID strategyId;
    UUID strategyIdNew;
    String SIEBEL_ID_MASTER = "1-3Z0IR7O";
    String SIEBEL_ID_SLAVE = "5-JEF71TBN";

    public String value;

    String description = "description test стратегия autotest update adjust base currency";

    @SneakyThrows
    @Test
    @Tag("qa2")
    @AllureId("731513")
    @DisplayName("C731513.HandleActualizeCommand.Определяем текущий портфель slave'a.Инициализация slave-портфеля с базовой валютой")
    @Subfeature("Успешные сценарии")
    @Description("Операция для обработки команд, направленных на актуализацию slave-портфеля.")
    void C731513() {
        String path = "/account/public/v1/invest/siebel/5-14HDAHOW7";
        String investId = "466e0a0b-b946-4697-8ba7-b727b42b6e06";
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + SIEBEL_ID_MASTER);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId(path, investId));
    }
}
