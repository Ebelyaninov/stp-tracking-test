package ru.qa.tinkoff.mocks.steps;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.mocks.steps.fireg.TradingShedulesExchangeSteps;
import ru.qa.tinkoff.mocks.steps.investmentAccount.MockInvestmentAccountSteps;
import ru.qa.tinkoff.mocks.steps.marketData.MockMarketDataSteps;
import ru.qa.tinkoff.mocks.steps.middle.MockMiddleSteps;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;


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
    @Autowired
    TradingShedulesExchangeSteps tradingShedulesExchangeSteps;





    public void createDataForMasterMock (String siebelIdMaster) {
        //Создание моков
        String investIdMaster = "61d87339-89fa-4c4a-aab7-d7573f92035e";
        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdMaster, investIdMaster));
        //GetBrockerAccountBySiebelId
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel(investIdMaster, siebelIdMaster, "2000006623"));
    }

    //Создание данных, для моков
    @SneakyThrows
    public void createDataForMocksTestC731513 (String siebelIdSlave, String investIdSlave, String  contractIdSlave, String ticker, String classCode, String quantityUsd) {
        String tickerAndClassCode = ticker + "_" + classCode;
        //Создание моков
        tradingShedulesExchangeSteps.clearTradingShedulesExchange();
        Thread.sleep(1000);
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB_MORNING_WEEKEND"));
        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdSlave);
        Thread.sleep(1000);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdSlave, investIdSlave));
        //GetBrockerAccountBySiebelId
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdSlave);
        Thread.sleep(1000);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps
            .createBodyForGetBrokerAccountBySiebel(investIdSlave, siebelIdSlave, contractIdSlave));
        //Очистить мок grpc
        mockMiddleSteps.clearMocksForGrpc();
        Thread.sleep(1000);
        //Добавляем данный grpc
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpc(contractIdSlave, "0", "0", quantityUsd, "0"));
        //Создаем цены в MD
        mockMarketDataSteps.clearMocks(tickerAndClassCode);
        ZonedDateTime date = LocalDateTime.now().withHour(0).atZone(ZoneId.of("Z"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "last", date.toString(), "108.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "bid", date.toString(), "109.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "ask", date.toString(), "107.22"));
        //Очищаем мок rest мок MD
        mockMiddleSteps.clearMocksForRestOrder();
        //Создать ответ от MD на order
        mockMiddleSteps.createRestOrder(mockMiddleSteps.createBodyForRestOrder(ticker, "Buy", contractIdSlave, classCode, "FillAndKill", "Fill", "1", "1", "AAA003484311"));
    }

    public void createDataForMocksTestC1366344 (String siebelIdSlave, String ticker, String classCode, String quantityUsd) {
        String tickerAndClassCode = ticker + "_" + classCode;
        //Создание моков
        String investIdSlave = "48d003df-6465-48a2-bb5e-8e6154ce182e";
        tradingShedulesExchangeSteps.clearTradingShedulesExchange();
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB_MORNING_WEEKEND"));
        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdSlave, investIdSlave));
        //GetBrockerAccountBySiebelId
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel(investIdSlave, siebelIdSlave, "2000066346"));
        //Очистить мок grpc
        mockMiddleSteps.clearMocksForGrpc();
        //Добавляем данный grpc
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpc("2000066346", quantityUsd, "0", "0", "0"));
        //Создаем цены в MD
        mockMarketDataSteps.clearMocks(tickerAndClassCode);
        ZonedDateTime date = LocalDateTime.now().withHour(0).atZone(ZoneId.of("Z"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "last", date.toString(), "108.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "bid", date.toString(), "109.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "ask", date.toString(), "107.22"));
//        //Очищаем мок rest мок MD
//        mockMiddleSteps.clearMocksForRestOrder();
//        //Создать ответ от MD на order
//        mockMiddleSteps.createRestOrder(mockMiddleSteps.createBodyForRestOrder(ticker, "Buy", "2000075370", classCode, "FillAndKill", "Fill", "1", "1", "AAA003484311"));
    }

    public void createDataForMocksTestC741543 (String siebelIdSlave, String ticker, String classCode, String usdQuantity, String quantityAAPL) {
        String tickerAndClassCode = ticker + "_" + classCode;
        //Создание моков
        String investIdSlave = "c76ab3d2-de36-4546-a0f2-0a2f8613a34e";
        tradingShedulesExchangeSteps.clearTradingShedulesExchange();
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB_MORNING_WEEKEND"));
        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdSlave, investIdSlave));
        //GetBrockerAccountBySiebelId
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel(investIdSlave, siebelIdSlave, "2000115978"));
        //Очистить мок grpc
        mockMiddleSteps.clearMocksForGrpc();
        //Добавляем данный grpc
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpc("2000115978", usdQuantity, "0", "0", quantityAAPL));
        //Создаем цены в MD
        mockMarketDataSteps.clearMocks(tickerAndClassCode);
        ZonedDateTime date = LocalDateTime.now().withHour(0).atZone(ZoneId.of("Z"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "last", date.toString(), "108.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "bid", date.toString(), "109.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "ask", date.toString(), "107.22"));
        //Очищаем мок rest мок MD
        mockMiddleSteps.clearMocksForRestOrder();
        //Создать ответ от MD на order
        mockMiddleSteps.createRestOrder(mockMiddleSteps.createBodyForRestOrder(ticker, "Buy", "2000115978", classCode, "FillAndKill", "Fill", "1", "1", "KNM219525193"));
    }

    public void createDataForMocksTestC695957 (String siebelIdSlave, String ticker, String classCode, String usdQuantity, String quantityAAPL) {
        String tickerAndClassCode = ticker + "_" + classCode;
        //Создание моков
        String investIdSlave = "c76ab3d2-de36-4546-a0f2-0a2f8613a34e";
        tradingShedulesExchangeSteps.clearTradingShedulesExchange();
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB_MORNING_WEEKEND"));
        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdSlave, investIdSlave));
        //GetBrockerAccountBySiebelId
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel(investIdSlave, siebelIdSlave, "2000115978"));
        //Очистить мок grpc
        mockMiddleSteps.clearMocksForGrpc();
        //Добавляем данный grpc
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpc("2000115978", usdQuantity, "0", "0", quantityAAPL));
        //Создаем цены в MD
        mockMarketDataSteps.clearMocks(tickerAndClassCode);
        ZonedDateTime date = LocalDateTime.now().withHour(0).atZone(ZoneId.of("Z"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "last", date.toString(), "108.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "bid", date.toString(), "109.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "ask", date.toString(), "107.22"));
        //Очищаем мок rest мок MD
        mockMiddleSteps.clearMocksForRestOrder();
        //Создать ответ от MD на order
        mockMiddleSteps.createRestOrder(mockMiddleSteps.createBodyForRestOrder(ticker, "Buy", "2000115978", classCode, "FillAndKill", "Fill", "1", "1", "KNM219525193"));
    }


    public void createDataForMockAnalizeBrokerAccount(String siebelIdMaster, String siebelIdSlave, String investIdMaster,
                                          String investIdSlave, String contractIdMaster, String contractIdSlave )
        throws InterruptedException {
        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdSlave);
        Thread.sleep(1000);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps
            .createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdSlave, investIdSlave));
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps
            .createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdSlave, investIdMaster));
        Thread.sleep(1000);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel
            (investIdMaster, siebelIdMaster, contractIdMaster));
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel
            (investIdSlave, siebelIdSlave, contractIdSlave));
    }


    public void createDataForMockAnalizeShedulesExchange(String exchange)
        throws InterruptedException {
        //Создание моков
        tradingShedulesExchangeSteps.clearTradingShedulesExchange();
        Thread.sleep(1000);
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange(exchange));
    }

    public void createDataForMockAnalizeShedulesExchangeFX(String exchange)
        throws InterruptedException {
        //Создание моков
        tradingShedulesExchangeSteps.clearTradingShedulesExchange();
        Thread.sleep(1000);
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchangeFX(exchange));
    }


    public void createDataForMockAnalizeMdPrices(String ticker, String classCode, String lastPrice, String bidPrice, String askPrice)
        throws InterruptedException {
        //Создание моков
        //Создаем цены в MD
        String tickerAndClassCode = ticker + "_" + classCode;
        mockMarketDataSteps.clearMocks(tickerAndClassCode);
        ZonedDateTime date = LocalDateTime.now().withHour(0).atZone(ZoneId.of("Z"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "last", date.toString(), lastPrice));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "bid", date.toString(), bidPrice));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "ask", date.toString(), askPrice));
        //Очищаем мок rest мок MD
    }

    public void createDataForMockAnalizeMdPrices(String contractIdSlave, String clientCode, String ticker, String classCode,
                                                 String action, String lotsRequested, String lotsExecuted)
        throws InterruptedException {
    //Очищаем мок rest мок middle
        mockMiddleSteps.clearMocksForRestOrder();
    //Создать ответ от MD на order
        mockMiddleSteps.createRestOrder(
            mockMiddleSteps.createBodyForRestOrder(ticker, action, contractIdSlave, classCode,
                "FillAndKill", "Fill", lotsRequested, lotsExecuted, clientCode));
}









}
