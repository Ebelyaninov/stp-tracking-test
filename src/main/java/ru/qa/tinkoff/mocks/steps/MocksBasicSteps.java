package ru.qa.tinkoff.mocks.steps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.mocks.steps.investmentAccount.MockInvestmentAccountSteps;
import ru.qa.tinkoff.mocks.steps.marketData.MockMarketDataSteps;
import ru.qa.tinkoff.mocks.steps.middle.MockMiddleSteps;


@Slf4j
@Service
@RequiredArgsConstructor
public class MocksBasicSteps {

    @Autowired
    MockInvestmentAccountSteps mockInvestmentAccountSteps;
    @Autowired
    MockMiddleSteps mockMiddleSteps;
    @Autowired
    MockMarketDataSteps mockMarketDataSteps;

    //Создание данных, для моков
    public void createDataForMocksTestC731513 () {
        //Создание моков
        String investIdMaster2 = "9850dcb7-85fb-4623-a960-24003f1c9629";
        String investIdSlave2 = "5460884c-be7d-400e-842d-059d0c689a55";
        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + "1-3Z0IR7O");
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId("/account/public/v1/invest/siebel/" + "1-3Z0IR7O", investIdMaster2));
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + "1-FRT3HXX");
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId("/account/public/v1/invest/siebel/" + "1-FRT3HXX", investIdSlave2));
        //GetBrockerAccountBySiebelId
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + "1-3Z0IR7O");
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel(investIdMaster2, "1-3Z0IR7O", "2054867557"));
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + "1-FRT3HXX");
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel( investIdSlave2, "1-FRT3HXX", "2000075370"));
        //Очистить мок grpc
        mockMiddleSteps.clearMocksForGrpc();
        //Добавляем данный grpc
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpc("2000075370", "7000"));
        //Создаем цены в MD
        mockMarketDataSteps.clearMocks("TSPX_TQTD");
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices("TSPX_TQTD", "last", "ts", "122.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices("TSPX_TQTD", "bid", "ts", "122.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices("TSPX_TQTD", "ask", "ts", "122.22"));
        //Очищаем мок rest мок MD
        mockMiddleSteps.clearMocksForRestOrder();
        //Создать ответ от MD на order
        mockMiddleSteps.createRestOrder(mockMiddleSteps.createBodyForRestOrder("AAPL", "Buy", "2000075370", "SPBXM", "FillAndKill", "Fill", "1", "1", "AAA003484311"));
    }
}
