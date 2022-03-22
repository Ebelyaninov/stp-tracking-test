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
import ru.qa.tinkoff.steps.trackingInstrument.StpInstrument;
import ru.qa.tinkoff.steps.trackingMockSlave.StpMockSlaveDate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.awaitility.Awaitility.await;


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
    @Autowired
    StpInstrument instrument;
    @Autowired
    StpMockSlaveDate stpMockSlaveDate;


    public void createDataForMasterMock (String siebelIdMaster) {
        //Создание моков
        String investIdMaster = stpMockSlaveDate.investIdMasterHandleActualizeCommand;
        String contractIdMaster = stpMockSlaveDate.contractIdMasterHandleActualizeCommand;
        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdMaster, investIdMaster));
        //GetBrockerAccountBySiebelId
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel(investIdMaster, siebelIdMaster, contractIdMaster));
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
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpc("2000115978", "0", "0", usdQuantity, "0",quantityAAPL,instrument.tickerAAPL, instrument.tradingClearingAccountAAPL));
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

    public void createDataForMocksForHandleActualizeCommand (String siebelIdSlave, String contractIdSlave, String ticker, String classCode, String tradingClearAccount, String rubQuantity, String usdQuantity, String usdScaledQty, String quantityForGRPCInstrument) {
        String tickerAndClassCode = ticker + "_" + classCode;
        //Создание моков
        String investIdSlave = stpMockSlaveDate.investIdSlaveHandleActualizeCommand;
        tradingShedulesExchangeSteps.clearTradingShedulesExchange();
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB_MORNING_WEEKEND"));
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchangeFX("FX"));
        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdSlave, investIdSlave));
        //GetBrockerAccountBySiebelId
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel(investIdSlave, siebelIdSlave, contractIdSlave));
        //Очистить мок grpc
        mockMiddleSteps.clearMocksForGrpc();
        //Добавляем данный grpc
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpc(contractIdSlave, "0", rubQuantity, usdQuantity, usdScaledQty, quantityForGRPCInstrument, ticker, tradingClearAccount));
        //Создаем цены в MD
        mockMarketDataSteps.clearMocks(tickerAndClassCode);
        String tickerAndClassCodeABBV = instrument.tickerABBV + "_" + instrument.classCodeABBV;
        mockMarketDataSteps.clearMocks(tickerAndClassCodeABBV);
        String tickerAndClassCodeHKD = instrument.tickerHKD + "_" + instrument.classCodeHKD;
        mockMarketDataSteps.clearMocks(tickerAndClassCodeHKD);
        String tickerAndClassCodeCHF = instrument.tickerCHF + "_" + instrument.classCodeCHF;
        mockMarketDataSteps.clearMocks(tickerAndClassCodeCHF);
        //Задержка 300мс
        await().pollDelay(Duration.ofMillis(300));
        ZonedDateTime date = LocalDateTime.now().withHour(0).atZone(ZoneId.of("Z"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "last", date.toString(), "108.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "bid", date.toString(), "109.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "ask", date.toString(), "107.22"));

        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeABBV, "last", date.toString(), "292"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeABBV, "bid", date.toString(), "289.4"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeABBV, "ask", date.toString(), "292"));

        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeHKD, "last", date.toString(), "292"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeHKD, "bid", date.toString(), "289"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeHKD, "ask", date.toString(), "292"));

        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeCHF, "last", date.toString(), "100.9825"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeCHF, "bid", date.toString(), "101.9825"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeCHF, "ask", date.toString(), "102.9825"));
        //Очищаем мок rest мок MD
        mockMiddleSteps.clearMocksForRestOrder();
        //Создать ответ от MD на order
        mockMiddleSteps.createRestOrder(mockMiddleSteps.createBodyForRestOrder(ticker, "Buy", contractIdSlave, classCode, "FillAndKill", "Fill", "1", "1", stpMockSlaveDate.clientCodeSlaveHandleActualizeCommand));
    }


    public void createDataForMockAnalizeBrokerAccount(String siebelIdMaster, String siebelIdSlave, String investIdMaster,
                                          String investIdSlave, String contractIdMaster, String contractIdSlave )
        throws InterruptedException {
        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdSlave);
        Thread.sleep(1000);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps
            .createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdSlave, investIdSlave));
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps
            .createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdMaster, investIdMaster));
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


    public void createDataForMockCreateSlaveOrders(String siebelIdMaster, String siebelIdSlave, String investIdMaster,
                                                   String investIdSlave, String contractIdMaster, String contractIdSlave,
                                                   String clientCode, String executionReportStatus, String ticker, String classCode,
                                                   String action, String lotsRequested, String lotsExecuted)
        throws InterruptedException {

        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdSlave);

        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps
            .createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdSlave, investIdSlave));
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps
            .createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdMaster, investIdMaster));

        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel
            (investIdMaster, siebelIdMaster, contractIdMaster));
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel
            (investIdSlave, siebelIdSlave, contractIdSlave));

        String tickerAndClassCode = ticker + "_" + classCode;
        //очищаем расписание
        tradingShedulesExchangeSteps.clearTradingShedulesExchange();
        //создаём расписание
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB_MORNING_WEEKEND"));

        //Создаем цены в MD
        mockMarketDataSteps.clearMocks(tickerAndClassCode);
        ZonedDateTime date = LocalDateTime.now().withHour(0).atZone(ZoneId.of("Z"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "last", date.toString(), "108.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "bid", date.toString(), "109.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "ask", date.toString(), "107.22"));;

        //Очищаем мок rest мок middle
        mockMiddleSteps.clearMocksForRestOrder();
        //Создать ответ от middle
        mockMiddleSteps.createRestOrder(
            mockMiddleSteps.createBodyForRestOrder(ticker, action, contractIdSlave, classCode,
                "FillAndKill", executionReportStatus, lotsRequested, lotsExecuted, clientCode));

    }


    public void createDataForMockCreateSlaveOrdersError(String siebelIdMaster, String siebelIdSlave, String investIdMaster,
                                                   String investIdSlave, String contractIdMaster, String contractIdSlave,
                                                   String clientCode, String ticker, String classCode,
                                                   String action, String message, String code)
        throws InterruptedException {

        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdSlave);

        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps
            .createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdSlave, investIdSlave));
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps
            .createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdMaster, investIdMaster));

        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel
            (investIdMaster, siebelIdMaster, contractIdMaster));
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel
            (investIdSlave, siebelIdSlave, contractIdSlave));

        String tickerAndClassCode = ticker + "_" + classCode;
        //очищаем расписание
        tradingShedulesExchangeSteps.clearTradingShedulesExchange();
        //создаём расписание
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB_MORNING_WEEKEND"));

        //Создаем цены в MD
        mockMarketDataSteps.clearMocks(tickerAndClassCode);
        ZonedDateTime date = LocalDateTime.now().withHour(0).atZone(ZoneId.of("Z"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "last", date.toString(), "108.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "bid", date.toString(), "109.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "ask", date.toString(), "107.22"));;

        //Очищаем мок rest мок middle
        mockMiddleSteps.clearMocksForRestOrder();
        //Создать ответ от middle
        mockMiddleSteps.createRestOrderError(
            mockMiddleSteps.createBodyForRestOrderError(ticker, action, contractIdSlave, classCode,
                "FillAndKill", message, code, clientCode));

    }



    public void TradingShedulesExchangeSetTime(String siebelIdMaster, String siebelIdSlave, String investIdMaster,
                                               String investIdSlave, String contractIdMaster, String contractIdSlave, String clientCode, String ticker, String classCode,
                                               String action, String lotsRequested, String lotsExecuted)
        throws InterruptedException{

        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdSlave);

        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps
            .createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdSlave, investIdSlave));
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps
            .createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdMaster, investIdMaster));

        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel
            (investIdMaster, siebelIdMaster, contractIdMaster));
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel
            (investIdSlave, siebelIdSlave, contractIdSlave));

        //очищаем расписание
        tradingShedulesExchangeSteps.clearTradingShedulesExchange();
        //создаём расписание
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchangeSetTime("SPB_MORNING_WEEKEND"));

        //Создаем цены в MD
        String tickerAndClassCode = ticker + "_" + classCode;
        mockMarketDataSteps.clearMocks(tickerAndClassCode);
        ZonedDateTime date = LocalDateTime.now().withHour(0).atZone(ZoneId.of("Z"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "last", date.toString(), "108.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "bid", date.toString(), "109.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "ask", date.toString(), "107.22"));;

        //Очищаем мок rest мок middle
        mockMiddleSteps.clearMocksForRestOrder();
        //Создать ответ от middle
        mockMiddleSteps.createRestOrder(
            mockMiddleSteps.createBodyForRestOrder(ticker, action, contractIdSlave, classCode,
                "FillAndKill", "Fill", lotsRequested, lotsExecuted, clientCode));


    }



}
