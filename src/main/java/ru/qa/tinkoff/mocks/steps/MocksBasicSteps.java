package ru.qa.tinkoff.mocks.steps;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.kafka.oldkafkaservice.OldKafkaService;
import ru.qa.tinkoff.kafka.services.StringSenderService;
import ru.qa.tinkoff.kafka.services.StringToByteSenderService;
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
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_SECOND;


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
//    @Autowired
//    OldKafkaService oldKafkaService;

    private final OldKafkaService oldKafkaService;


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
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("MOEX"));
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB_MORNING"));
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


    public void createDataForMocksSlaveVersionsGRPC (String siebelIdSlave, String contractIdSlave, String usdScaledQty, String quantity, String ticker, String tradingAccount, String quantityCCL) {
        //String tickerAndClassCode = ticker + "_" + classCode;
        //Создание моков
        String investIdSlave = stpMockSlaveDate.investIdSlaveHandleActualizeCommand;
        tradingShedulesExchangeSteps.clearTradingShedulesExchange();
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB"));
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchangeFX("SPB_MORNING"));
        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdSlave, investIdSlave));
        //GetBrockerAccountBySiebelId
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel(investIdSlave, siebelIdSlave, contractIdSlave));
        //Очистить мок grpc
        mockMiddleSteps.clearMocksForGrpc();
        //Добавляем данный grpc
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpcOne(contractIdSlave, "300", usdScaledQty, quantity, ticker, tradingAccount, quantityCCL, instrument.tickerCCL, instrument.tradingClearingAccountCCL));
        //Создаем цены в MD
        //mockMarketDataSteps.clearMocks(tickerAndClassCode);
        String tickerAndClassCodeFB = instrument.tickerFB + "_" + instrument.classCodeFB;
        mockMarketDataSteps.clearMocks(tickerAndClassCodeFB);
        String tickerAndClassCodeCCL = instrument.tickerCCL + "_" + instrument.classCodeCCL;
        mockMarketDataSteps.clearMocks(tickerAndClassCodeCCL);
        String tickerAndClassCodeAAPL = instrument.tickerAAPL + "_" + instrument.classCodeAAPL;
        mockMarketDataSteps.clearMocks(tickerAndClassCodeAAPL);
        //Задержка 300мс
        await().pollDelay(Duration.ofMillis(300));
        ZonedDateTime date = LocalDateTime.now().withHour(0).atZone(ZoneId.of("Z"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeAAPL, "last", date.toString(), "108.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeAAPL, "bid", date.toString(), "109.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeAAPL, "ask", date.toString(), "107.22"));

        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeFB, "last", date.toString(), "500"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeFB, "bid", date.toString(), "500.4"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeFB, "ask", date.toString(), "500"));

        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeCCL, "last", date.toString(), "292"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeCCL, "bid", date.toString(), "289"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeCCL, "ask", date.toString(), "292"));

/*        //Очищаем мок rest мок MD
        mockMiddleSteps.clearMocksForRestOrder();
        //Создать ответ от MD на order
        mockMiddleSteps.createRestOrder(mockMiddleSteps.createBodyForRestOrder(ticker, "Buy", contractIdSlave, classCode, "FillAndKill", "Fill", "1", "1", stpMockSlaveDate.clientCodeSlaveHandleActualizeCommand));*/
    }


    public void createDataForMocksSlaveGRPC (String siebelIdSlave, String contractIdSlave, String usdQuantity, String usdScaledQty, String quantity, String tradingClearAccount, String ticker) {
        //String tickerAndClassCode = ticker + "_" + classCode;
        //Создание моков
        String investIdSlave = stpMockSlaveDate.investIdSlaveHandleActualizeCommand;
        tradingShedulesExchangeSteps.clearTradingShedulesExchange();
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB"));
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchangeFX("SPB_MORNING"));
        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdSlave, investIdSlave));
        //GetBrockerAccountBySiebelId
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel(investIdSlave, siebelIdSlave, contractIdSlave));
        //Очистить мок grpc
        mockMiddleSteps.clearMocksForGrpc();
        //Добавляем данный grpc
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpcTwo(contractIdSlave, usdQuantity, usdScaledQty, quantity, ticker, tradingClearAccount));
        //Создаем цены в MD
        //mockMarketDataSteps.clearMocks(tickerAndClassCode);
        String tickerAndClassCodeFB = instrument.tickerFB + "_" + instrument.classCodeFB;
        mockMarketDataSteps.clearMocks(tickerAndClassCodeFB);
        String tickerAndClassCodeCCL = instrument.tickerCCL + "_" + instrument.classCodeCCL;
        mockMarketDataSteps.clearMocks(tickerAndClassCodeCCL);
        String tickerAndClassCodeAAPL = instrument.tickerAAPL + "_" + instrument.classCodeAAPL;
        mockMarketDataSteps.clearMocks(tickerAndClassCodeAAPL);
        //Задержка 300мс
        await().pollDelay(Duration.ofMillis(300));
        ZonedDateTime date = LocalDateTime.now().withHour(0).atZone(ZoneId.of("Z"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeAAPL, "last", date.toString(), "108.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeAAPL, "bid", date.toString(), "109.22"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeAAPL, "ask", date.toString(), "107.22"));

        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeFB, "last", date.toString(), "500"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeFB, "bid", date.toString(), "500.4"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeFB, "ask", date.toString(), "500"));

        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeCCL, "last", date.toString(), "292"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeCCL, "bid", date.toString(), "289"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeCCL, "ask", date.toString(), "292"));

        //Очищаем мок rest мок MD
        mockMiddleSteps.clearMocksForRestOrder();
        //Создать ответ от MD на order
        mockMiddleSteps.createRestOrder(mockMiddleSteps.createBodyForRestOrder(ticker, "Buy", contractIdSlave, instrument.classCodeAAPL, "FillAndKill", "Fill", "5", "5", "770016286649"));
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


    public void createShedulesEMockAnalizeShedulesExchange()
        throws InterruptedException {
        //Создание моков
        tradingShedulesExchangeSteps.clearTradingShedulesExchange();
        Thread.sleep(1000);
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB_MORNING"));
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB"));
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("MOEX"));
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("MOEX_PLUS"));
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("MOEX_MORNING"));
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("FX"));
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("MOEX_PLUS_WEEKEND"));

    }


    public void createDataForMockAnalizeMdPrices(String ticker, String classCode, String lastPrice, String bidPrice, String askPrice)
        throws InterruptedException {
        //Создание моков
        //Создаем цены в MD
        String tickerAndClassCode = ticker + "_" + classCode;
        mockMarketDataSteps.clearMocks(tickerAndClassCode);
        Thread.sleep(1000);
        ZonedDateTime date = LocalDateTime.now().withHour(0).atZone(ZoneId.of("Z"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "last", date.toString(), lastPrice));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "bid", date.toString(), bidPrice));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "ask", date.toString(), askPrice));
        //Очищаем мок rest мок MD
    }


    public void createDataForMockMdPrices(String instrumentId, String lastPrice, String bidPrice, String askPrice)
        throws InterruptedException {
        //Создание моков
        ZonedDateTime date = LocalDateTime.now().withHour(0).atZone(ZoneId.of("Z"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(instrumentId, "last", date.toString(), lastPrice));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(instrumentId, "bid", date.toString(), bidPrice));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(instrumentId, "ask", date.toString(), askPrice));

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
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdMaster, investIdMaster));
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdSlave, investIdSlave));
        //GetBrockerAccountBySiebelId
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel(investIdMaster, siebelIdMaster, contractIdMaster));
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel(investIdSlave, siebelIdSlave, contractIdSlave));


        String tickerAndClassCode = ticker + "_" + classCode;
        //очищаем расписание
        tradingShedulesExchangeSteps.clearTradingShedulesExchange();
        //создаём расписание
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB"));
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB_MORNING_WEEKEND"));
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB_MORNING"));

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
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB_MORNING"));

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



    public void TradingShedulesExchangeDefaultTime(String siebelIdMaster, String siebelIdSlave, String investIdMaster,
                                               String investIdSlave, String contractIdMaster, String contractIdSlave, String clientCode, String ticker, String classCode,
                                               String action, String lotsRequested, String lotsExecuted, String exchange)
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
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchangeDefaultTime(exchange));

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

    @SneakyThrows
    public void createDataForMocksForSynchronizePositionResolver (String siebelIdSlave, String contractIdSlave, String ticker, String classCode, String tradingClearAccount, String actionForMiddleRestOrder, String rubQuantity, String usdQuantity, String usdScaledQty, String quantityForGRPCInstrument) {
        String tickerAndClassCode = ticker + "_" + classCode;
//        //Создание моков fireg
//        String investIdSlave = stpMockSlaveDate.investIdSlaveSynchronizePositionResolver;
//        //getInvestID
//        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdSlave);
//        Thread.sleep(200);
//        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdSlave, investIdSlave));
//        //GetBrockerAccountBySiebelId
//        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdSlave);
//        Thread.sleep(200);
//        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel(investIdSlave, siebelIdSlave, contractIdSlave));

        //Очистить мок grpc
        mockMiddleSteps.clearMocksForGrpc();
        //Добавляем данный grpc
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpc(contractIdSlave, "0", rubQuantity, usdQuantity, usdScaledQty, quantityForGRPCInstrument, ticker, tradingClearAccount));
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpc(contractIdSlave, "0", rubQuantity, "39", usdScaledQty, quantityForGRPCInstrument, instrument.tickerUSDRUB, instrument.tradingClearingAccountUSDRUB));
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpc(contractIdSlave, "0", rubQuantity, usdQuantity, usdScaledQty, quantityForGRPCInstrument, instrument.tickerEURRUB, instrument.tradingClearingAccountEURRUB));
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpc(contractIdSlave, "0", rubQuantity, usdQuantity, usdScaledQty, quantityForGRPCInstrument, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL));
        //Создаем цены в MD
//        mockMarketDataSteps.clearMocks(tickerAndClassCode);
        String tickerAndClassCodeABBV = instrument.tickerABBV + "_" + instrument.classCodeABBV;
//        mockMarketDataSteps.clearMocks(tickerAndClassCodeABBV);
        String tickerAndClassCodeQCOM = instrument.tickerQCOM + "_" + instrument.classCodeQCOM;
//        mockMarketDataSteps.clearMocks(tickerAndClassCodeQCOM);
        String tickerAndClassCodeUSDRUB = instrument.tickerUSDRUB + "_" + instrument.classCodeUSDRUB;
//        mockMarketDataSteps.clearMocks(tickerAndClassCodeUSDRUB);
        String tickerAndClassCodeEURRUB = instrument.tickerEURRUB + "_" + instrument.classCodeEURRUB;
//        mockMarketDataSteps.clearMocks(tickerAndClassCodeEURRUB);
        String tickerAndClassCodeALFAperp = instrument.tickerALFAperp + "_" + instrument.classCodeALFAperp;
//        mockMarketDataSteps.clearMocks(tickerAndClassCodeALFAperp);
        String tickerAndClassCodeXS0191754729 = instrument.tickerXS0191754729 + "_" + instrument.classCodeXS0191754729;
//        mockMarketDataSteps.clearMocks(tickerAndClassCodeXS0191754729);
        String tickerAndClassCodeAAPL = instrument.tickerAAPL + "_" + instrument.classCodeAAPL;
//        mockMarketDataSteps.clearMocks(tickerAndClassCodeAAPL);
        String tickerAndClassCodeSBER = instrument.tickerSBER + "_" + instrument.classCodeSBER;
//        mockMarketDataSteps.clearMocks(tickerAndClassCodeSBER);
        String tickerAndClassCodeGBPRUB = instrument.tickerGBP + "_" + instrument.classCodeGBP;
//        mockMarketDataSteps.clearMocks(tickerAndClassCodeGBPRUB);
        String tickerAndClassCodeVTBM = instrument.tickerVTBM + "_" + instrument.classCodeVTBM;
//        mockMarketDataSteps.clearMocks(tickerAndClassCodeVTBM);
        String tickerAndClassCodeSU29009RMFS6 = instrument.tickerSU29009RMFS6 + "_" + instrument.classCodeSU29009RMFS6;
//        mockMarketDataSteps.clearMocks(tickerAndClassCodeSU29009RMFS6);
        //Задержка 300мс
        await().pollDelay(Duration.ofMillis(300));
        ZonedDateTime date = LocalDateTime.now().withHour(0).atZone(ZoneId.of("Z"));

        // моки MarketData
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "last", date.toString(), "108.22"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "bid", date.toString(), "109.22"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "ask", date.toString(), "107.22"));
//
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeABBV, "last", date.toString(), "292"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeABBV, "bid", date.toString(), "289.4"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeABBV, "ask", date.toString(), "292"));
//
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeQCOM, "last", date.toString(), "292"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeQCOM, "bid", date.toString(), "289"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeQCOM, "ask", date.toString(), "292"));
//
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeUSDRUB, "last", date.toString(), "96.36"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeUSDRUB, "bid", date.toString(), "95.3975"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeUSDRUB, "ask", date.toString(), "93.955"));
//
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeEURRUB, "last", date.toString(), "105.37"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeEURRUB, "bid", date.toString(), "105.37"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeEURRUB, "ask", date.toString(), "105.99"));
//
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeALFAperp, "last", date.toString(), "100.9825"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeALFAperp, "bid", date.toString(), "101.9825"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeALFAperp, "ask", date.toString(), "102.9825"));
//
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeXS0191754729, "last", date.toString(), "100.9825"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeXS0191754729, "bid", date.toString(), "101.9825"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeXS0191754729, "ask", date.toString(), "102.9825"));
//
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeAAPL, "last", date.toString(), "100.9825"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeAAPL, "bid", date.toString(), "101.9825"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeAAPL, "ask", date.toString(), "102.9825"));
//
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeSBER, "last", date.toString(), "123.7"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeSBER, "bid", date.toString(), "123.7"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeSBER, "ask", date.toString(), "126.8"));
//
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeGBPRUB, "last", date.toString(), "108.22"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeGBPRUB, "bid", date.toString(), "109.22"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeGBPRUB, "ask", date.toString(), "107.22"));
//
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeVTBM, "last", date.toString(), "108.22"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeVTBM, "bid", date.toString(), "109.22"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeVTBM, "ask", date.toString(), "107.22"));
//
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeSU29009RMFS6, "last", date.toString(), "108.22"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeSU29009RMFS6, "bid", date.toString(), "109.22"));
//        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCodeSU29009RMFS6, "ask", date.toString(), "107.22"));
        //Очищаем мок rest мок MD
//        mockMiddleSteps.clearMocksForRestOrder();
        //Создать ответ от MD на order
//        mockMiddleSteps.createRestOrder(mockMiddleSteps.createBodyForRestOrder(ticker, actionForMiddleRestOrder, contractIdSlave, classCode, "FillAndKill", "Fill", "1", "1", stpMockSlaveDate.clientCodeSynchronizePositionResolver));
        //        JSONObject createdKeyJson = new JSONObject();
//        createdKeyJson.put("instrument_id", tickerAndClassCode);
//
//        JSONObject createdValueJson = new JSONObject();
//        createdValueJson.put("instrument_uid", UUID.randomUUID());
//        createdValueJson.put("instrument_id", tickerAndClassCode);
//        createdValueJson.put("status", "normal_trading");
//        createdValueJson.put("ts", date.plusHours(3));
//
//        oldKafkaService.send(Topics.TEST_TOPIC_TO_DELETE, createdKeyJson.toString(), createdValueJson.toString());
    }

    @SneakyThrows
    public void createTradingShedules() {
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("SPB_MORNING"));
        Thread.sleep(100);
//        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchangeFX("FX_WEEKEND"));
        Thread.sleep(100);
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchangeFX("FX"));
        Thread.sleep(100);
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange("MOEX_PLUS"));
        Thread.sleep(100);
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForMOEXMORNINGTradingShedulesExchange("MOEX_MORNING"));
        Thread.sleep(100);
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchangeFX("SPB"));
        Thread.sleep(100);
//        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchangeFX("SPB_MORNING_WEEKEND"));
        Thread.sleep(100);
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchangeFX("MOEX_PLUS_WEEKEND"));
        Thread.sleep(100);
    }

    public void createShedulesToMockAnalizeExchangeFX(String exchange)
        throws InterruptedException {
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchangeFX(exchange));
    }

    public void createShedulesToMockAnalizeExchange(String exchange)
        throws InterruptedException {
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchange(exchange));
    }


    public void createDataForMasterMockApi (String siebelIdMaster) {
        //Создание моков
        String investIdMaster = stpMockSlaveDate.investIdMasterApi;
        String contractIdMaster = stpMockSlaveDate.contractIdMasterApi;
        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdMaster, investIdMaster));
        //GetBrockerAccountBySiebelId
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdMaster);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel(investIdMaster, siebelIdMaster, contractIdMaster));
    }


    public void createDataForMasterSignal (String ticker, String classCode, String firstExchange, String secondExchange, String price) {
        String tickerAndClassCode = ticker + "_" + classCode;
        //очищаем расписание
        tradingShedulesExchangeSteps.clearTradingShedulesExchange();
        //создаём расписание
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchangeDefaultTime(firstExchange));
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchangeDefaultTime(secondExchange));
        //Создаем цены в MD
        mockMarketDataSteps.clearMocks(tickerAndClassCode);
        ZonedDateTime date = LocalDateTime.now().withHour(0).atZone(ZoneId.of("Z"));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "last", date.toString(), price));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "bid", date.toString(), price));
        mockMarketDataSteps.createRestMock(mockMarketDataSteps.createBodyForInstrumentPrices(tickerAndClassCode, "ask", date.toString(), price));;

    }

    public void createDataForMockSynchronizationCommand(String siebelIdSlave, String investIdSlave,  String contractIdSlave,
                                                        String tradingClearAccount, String rubQuantity, String usdQuantity,
                                                        String usdScaledQty, String quantityForGRPCInstrument, String clientCode,
                                                        String executionReportStatus, String ticker, String classCode,
                                                        String action, String lotsRequested, String lotsExecuted)
        throws InterruptedException {

        //getInvestID
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/invest/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetInvestId("/account/public/v1/invest/siebel/" + siebelIdSlave, investIdSlave));
        //GetBrockerAccountBySiebelId
        mockInvestmentAccountSteps.clearMocks("/account/public/v1/broker-account/siebel/" + siebelIdSlave);
        mockInvestmentAccountSteps.createRestMock(mockInvestmentAccountSteps.createBodyForGetBrokerAccountBySiebel(investIdSlave, siebelIdSlave, contractIdSlave));

        String tickerAndClassCode = ticker + "_" + classCode;
        //очищаем расписание
        tradingShedulesExchangeSteps.clearTradingShedulesExchange();
        //создаём расписание
        tradingShedulesExchangeSteps.createTradingShedulesExchange(tradingShedulesExchangeSteps.createBodyForTradingShedulesExchangeDefaultTime("SPB_MORNING"));

        //Очистить мок grpc
        mockMiddleSteps.clearMocksForGrpc();
        //Добавляем данный grpc
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpc(contractIdSlave, "0", rubQuantity, usdQuantity, usdScaledQty, quantityForGRPCInstrument, ticker, tradingClearAccount));
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpc(contractIdSlave, "0", rubQuantity, "39", usdScaledQty, quantityForGRPCInstrument, instrument.tickerUSDRUB, instrument.tradingClearingAccountUSDRUB));
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpc(contractIdSlave, "0", rubQuantity, usdQuantity, usdScaledQty, quantityForGRPCInstrument, instrument.tickerEURRUB, instrument.tradingClearingAccountEURRUB));
        mockMiddleSteps.createGrpcMock(mockMiddleSteps.createBodyForGrpc(contractIdSlave, "0", rubQuantity, usdQuantity, usdScaledQty, quantityForGRPCInstrument, instrument.tickerAAPL, instrument.tradingClearingAccountAAPL));


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



}
