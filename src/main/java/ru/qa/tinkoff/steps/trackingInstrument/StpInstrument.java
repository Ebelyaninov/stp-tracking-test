package ru.qa.tinkoff.steps.trackingInstrument;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

//здесь храним список используемых инструментов в авто-тестах
@Slf4j
@Service
@RequiredArgsConstructor
public class StpInstrument {

    public String tickerAEE1 = "AAPL";
    public String tradingClearingAccountAEE1 = "L01+00000BLP";
    public String classCodeAEE1 = "SPBXM";
    public String instrumentAEE1 = tickerAEE1 + "_" + classCodeAEE1;

    public String tickerAAPL = "AAPL";
    public String tradingClearingAccountAAPL = "TKCBM_TCAB";
    public UUID positionIdAAPL = UUID.fromString("5c5e6656-c4d3-4391-a7ee-e81a76f1804e");
//    public String tradingClearingAccountAAPL1 = "L01+00000SPB";
    public String typeAAPL = "share";
    public String classCodeAAPL = "SPBXM";
    public String briefNameAAPL = "Apple";
    public String imageAAPL = "US0378331005.png";
    public String currencyAAPL = "usd";
    public String instrumentAAPL = tickerAAPL + "_" + classCodeAAPL;


    public String tickerABBV = "ABBV";
    public String tradingClearingAccountABBV = "TKCBM_TCAB";
    public UUID positionIdABBV = UUID.fromString("4800523a-8e7c-48f7-8bf1-2a9e2a84378d");
    public String classCodeABBV = "SPBXM";
    public String instrumentABBV = tickerABBV + "_" + classCodeABBV;

    public String tickerSBER = "SBER";
    public String tradingClearingAccountSBER = "L01+00002F00";
    public String tradingClearingAccountSBER1 = "L01+00000F00";
    public UUID positionIdSBER = UUID.fromString("41eb2102-5333-4713-bf15-72b204c4bf7b");
    public String sectorSBER = "financial";
    public String typeSBER = "share";
    public String companySBER = "Сбер Банк";
    public String classCodeSBER = "TQBR";
    public String instrumentSBER = tickerSBER + "_" + classCodeSBER;
    public String briefNameSBER ="Сбер Банк";
    public String imageSBER ="sber.png";


    public String tickerFB = "META";
    public String tradingClearingAccountFB = "TKCBM_TCAB";
    public UUID positionIdFB = UUID.fromString("fce134ae-bb91-498c-aa5d-4f49ad2e5392");
    public String typeFB = "share";
    public String classCodeFB = "SPBXM";
    public String briefNameFB = "Meta Platforms";
    public String imageFB = "000000.png";
    public String currencyFB = "usd";
    public String instrumentFB = tickerFB + "_" + classCodeFB;

    public String tickerNOK = "NOK";
    public String classCodeNOK = "SPBXM";
    //public String tradingClearingAccountNOK = "L01+00000SPB";
    public String tradingClearingAccountNOK = "TKCBM_TCAB";
    public String briefNameNOK = "Nokia";
    public String imageNOK = "US6549022043.png";
    public String typeNOK = "share";
    public UUID positionIdNOK = UUID.fromString("dbd150e9-799f-431c-8693-bd6d25892122");



    public String tickerFXDE = "FXDE";
    public String classCodeFXDE = "TQTF";
    public String tradingClearingAccountFXDE = "L01+00002F00";
    public String briefNameFXDE = "FinEx Акции немецких компаний";
    public String imageFXDE = "IE00BD3QJN10.png";
    public String typeFXDE = "etf";

    public String tickerFXGD = "FXGD";
    public String classCodeFXGD = "TQTF";
    public String tradingClearingAccountFXGD = "L01+00000F00";


    public String tickerXS0191754729 = "XS0191754729";
    //public String tradingClearingAccountXS0191754729 = "L01+00000F00";
    public String tradingClearingAccountXS0191754729 = "L01+00002F00";
    public UUID positionIdXS0191754729 = UUID.fromString("9bbe359f-3345-4b14-b275-b907e266dc3d");

    public String typeXS0191754729 = "bond";
    public String classCodeXS0191754729 = "TQOD";
    public String instrumentXS0191754729 =tickerXS0191754729 + "_" + classCodeXS0191754729;
    public String briefNameXS0191754729 = "Gazprom";
    public String imageXS0191754729 = "RU0007661625.png";
    public String currencyXS0191754729 = "usd";


    public String tickerSU29009RMFS6 = "SU29009RMFS6";
    public String tradingClearingAccountSU29009RMFS6 = "L01+00002F00";
    public String classCodeSU29009RMFS6 = "TQOB";
    public String sectorSU29009RMFS6 = "government";
    public String typeSU29009RMFS6 = "bond";
    public String companySU29009RMFS6 = "ОФЗ";
    public String instrumentSU29009RMFS6 = tickerSU29009RMFS6 + "_" + classCodeSU29009RMFS6;
    public String briefNameSU29009RMFS6 = "ОФЗ 29009";
    public String imageSU29009RMFS6 = "minfin.png";

    public String tickerLKOH = "LKOH";
    public String tradingClearingAccountLKOH = "L01+00000F00";
    public String classCodeLKOH = "TQBR";
    public String sectorLKOH = "energy";
    public String typeLKOH = "share";
    public String companyLKOH = "Лукойл";
    public String instrumentLKOH = tickerLKOH + "_" + classCodeLKOH;


    public String tickerSNGSP = "SNGSP";
    public String tradingClearingAccountSNGSP = "L01+00000F00";
    public String classCodeSNGSP = "TQBR";
    public String sectorSNGSP = "energy";
    public String typeSNGSP = "share";
    public String companySNGSP = "Сургутнефтегаз";
    public String instrumentSNGSP = tickerSNGSP + "_" + classCodeSNGSP;


    public String tickerTRNFP = "TRNFP";
    public String tradingClearingAccountTRNFP = "L01+00000F00";
    public String classCodeTRNFP = "TQBR";
    public String sectorTRNFP = "energy";
    public String typeTRNFP = "share";
    public String companyTRNFP = "Транснефть";
    public String instrumentTRNFP = tickerTRNFP + "_" + classCodeTRNFP;


    public String tickerESGR = "ESGR";
    public String tradingClearingAccountESGR = "L01+00000F00";
    public String classCodeESGR = "TQTF";
    public String sectorESGR = "other";
    public String typeESGR = "etf";
    public String companyESGR = "РСХБ Управление Активами";
    public String instrumentESGR = tickerESGR + "_" + classCodeESGR;


    public String tickerYNDX = "YNDX";
    public String tradingClearingAccountYNDX = "L01+00002F00";
    public UUID positionIdYNDX = UUID.fromString("cb51e157-1f73-4c62-baac-93f11755056a");
//    public String tradingClearingAccountYNDX = "Y02+00001F00";
    public String classCodeYNDX = "TQBR";
    public String sectorYNDX = "telecom";
    public String typeYNDX = "share";
    public String companyYNDX = "Яндекс";
    public String instrumentYNDX = tickerYNDX + "_" + classCodeYNDX;


    public String tickerUSD = "USD000UTSTOM";
    public String tradingClearingAccountUSD = "MB9885503216";
    public UUID positionIdUSD = UUID.fromString("6e97aa9b-50b6-4738-bce7-17313f2b2cc2");
    public String classCodeUSD = "CETS";
    public String sectorUSD = "money";
    public String typeUSD = "money";
    public String companyUSD = "Другое";
    public String instrumentUSD = tickerUSD + "_" + classCodeUSD;
    public String briefNameUSD = "Доллар США";
    public String imageUSD = "USD.png";
    public String currencyUSD = "rub";
    public String typeCurUSD = "currency";


    public String tickerEURRUBTOM = "EUR_RUB__TOM";
    public String tradingClearingAccountEURRUBTOM = "MB9885503216";
    public String classCodeEURRUBTOM = "CETS";
    public String instrumentEURRUBTOM = tickerEURRUBTOM + "_" + classCodeEURRUBTOM;

    public String tickerEURRUB = "EURRUB";
    public String tradingClearingAccountEURRUB = "MB9885503216";
    public String classCodeEURRUB = "EES_CETS";


    public String tickerXS0587031096 = "XS0587031096";
    public String classCodeXS0587031096 = "RPEU";
    public String tradingClearingAccountXS0587031096 = "L01+00000SPB";

    public String tickerTRUR = "TRUR";
    public String classCodeTRUR = "TQTF";
//    public String tradingClearingAccountTRUR = "L01+00000F00";
    public String tradingClearingAccountTRUR = "L01+00002F00";
    public UUID positionIdTRUR = UUID.fromString("8005e2ec-66b3-49ae-9711-a424d9c9b61b");


    public String tickerXS0424860947 = "XS0424860947";
    public String classCodeXS0424860947 = "TQOD";
    public String tradingClearingAccountXS0424860947 = "L01+00000F00";
    public UUID positionIdXS0424860947 = UUID.fromString("aa9fc0b7-7eea-4b15-b753-89bfb3fed79d");

    public String tickerALFAperp = "ALFAperp";
    public String tradingClearingAccountALFAperp = "TKCBM_TCAB";
    public UUID positionIdALFAperp = UUID.fromString("bfa1b011-c9a0-4126-92f8-0d7a80aebbf1");
    public String classCodeALFAperp = "SPBBND";
    public String instrumentALFAperp = tickerALFAperp + "_" + classCodeALFAperp;


    public String tickerXS1589324075 = "XS1589324075";
    public String classCodeXS1589324075 = "TQOD";
    public String tradingClearingAccountXS1589324075 = "L01+00000F00";


    public String tickerSBERT = "SBERT";
    public String tradingClearingAccountSBERT = "L01+00002F00";
    public String classCodeSBERT = "TQBR";


    public String tickerAFX = "AFX@DE";
    public String tradingClearingAccountAFX = "L01+00000SPB";
    public String classCodeAFX = "SPBDE";


    public String tickerTCSG = "TCSG";
    public String tradingClearingAccountTCSG = "L01+00000F00";
    public String classCodeTCSG = "TQBR";

    public String tickerTBIO = "TBIO";
    public String tradingClearingAccountTBIO = "NDS000000001";
    public String classCodeTBIO = "TQTF";

    public String tickerUSDRUB = "USDRUB";
    public String tradingClearingAccountUSDRUB = "MB9885503216";
    public String classCodeUSDRUB = "EES_CETS";
    public String instrumentUSDRUB = tickerUSDRUB + "_" + classCodeUSDRUB;


    public String tickerGBP = "GBPRUB";
    public String tradingClearingAccountGBP = "MB9885503216";
    public String classCodeGBP = "EES_CETS";
    public String instrumentGBP = tickerGBP + "_" + classCodeGBP;

    public String tickerJPY = "JPYRUB";
    public String tradingClearingAccountJPY = "MB9885503216";

    public String tickerRUB = "RUB";
    public String tradingClearingAccountRUB = "MB9885503216";


    public String tickerCHF = "CHFRUB";
    public String tradingClearingAccountCHF = "MB9885503216";
    public String classCodeCHF = "EES_CETS";

    public String tickerHKD = "HKDRUB";
    public String tradingClearingAccountHKD = "MB9885503216";
    public String classCodeHKD = "EES_CETS";
    public String instrumentHKD = tickerHKD + "_" + classCodeHKD;

    //нет в кеше exchangePositionCache
    public String tickerTEST = "TEST";
    public String tradingClearingAccountTEST = "L01+00002F00";
    public String classCodeTEST = "TQBR";
    public String instrumentTEST = tickerTEST + "_" + classCodeTEST;


    //нет в кеше exchangePositionPriceCache
    public String tickerFXITTEST = "FXITTEST";
    public String tradingClearingAccountFXITTEST = "L01+00002F00";
    public String classCodeFXITTEST = "TQBR";
    public String instrumentFXITTEST = tickerFXITTEST + "_" + classCodeFXITTEST;


    public String tickerXS0088543190 = "XS0088543190";
    public String classCodeXS0088543190 = "SBTBSLT";
    public String tradingClearingAccountXS0088543190 = "L01+00002F00";

    public String tickerNMR = "NMR";
    public String classCodeNMR = "NMR";
    public String tradingClearingAccountNMR = "NDS000000001";
    public UUID positionIdNMR = UUID.fromString("ca958117-4eb8-4c30-af35-42367c5233d0");

    public String tickerBCR = "BCR";
    public String classCodeBCR = "SPBXM";
    public String tradingClearingAccountBCR = "L01+00000SPB";

    public String tickerBRJ1 = "BRJ1";
    public String classCodeBRJ1 = "SPBFUT";
    public String tradingClearingAccountBRJ1 = "U800";


    public String tickerQCOM = "QCOM";
    //public String tradingClearingAccountQCOM = "L01+00000SPB";
    public String tradingClearingAccountQCOM = "TKCBM_TCAB";
    public String classCodeQCOM = "SPBXM";


    public String tickerTUSD = "TUSD";
    public String tradingClearingAccountTUSD = "L01+00000F00";
    public String classCodeTUSD = "TQTD";
    public String instrumentTUSD = tickerTUSD + "_" + classCodeTUSD;

    public String tickerTECH = "TECH";
    public String tradingClearingAccountTECH = "L01+00000SPB";
    public String classCodeTECH = "SPBXM";
    public String instrumentTECH = tickerTECH + "_" + classCodeTECH;

    public String tickerBANEP = "BANEP";
    public String classCodeBANEP = "TQBR";
    public String tradingClearingAccountBANEP = "L01+00000F00";

    public String tickerGLDRUB = "GLDRUB_TOM";
    public String classCodeGLDRUB = "CETS";
    public String tradingClearingAccountGLDRUB = "MB9885503216";
    public String instrumentGLDRUB = tickerGLDRUB + "_" + classCodeGLDRUB;

    public String tickerKZT = "KZTRUB_TOM";
    public String tradingClearingAccountKZT = "MB9885503216";
    public String classCodeKZT = "CETS";

    public String tickerBYN = "BYNRUB_TOM";
    public String tradingClearingAccountBYN = "MB9885503216";
    public String classCodeBYN = "CETS";

    public String tickerXAU = "GLDRUB_TOM";
    public String tradingClearingAccountXAU = "MB9885503216";
    public String classCodeXAU = "CETS";

    public String tickerXAG = "SLVRUB_TOM";
    public String tradingClearingAccountXAG = "MB9885503216";
    public String classCodeXAG = "CETS";

    public String tickerMTS0620 = "MTS0620";
    public String tradingClearingAccountMTS0620 = "L01+00000SPB";
    public String classCodeMTS0620 = "SPBBND";

    public String tickerCCL = "CCL";
    public String tradingClearingAccountCCL = "L01+00000SPB";
    public String classCodeCCL = "SPBXM";

    public String tickerSTM = "STM";
    public String classCodeSTM = "STM";
    public String tradingClearingAccountSTM = "NDS000000001";
    public UUID   positionIdSTM = UUID.fromString("0a0e23ad-efe1-4d35-b59b-aa9cb718d6eb");

    public String tickerLNT = "LNT";
    public String classCodeLNT = "SPBXM";
    public String tradingClearingAccountLNT = "L01+00000SPB";
    public UUID positionIdLNT = UUID.fromString("69a8b572-008d-4b22-bdaa-e56b7cd740bc");

    public String tickerVTBM = "VTBM";
    //public String tradingClearingAccountVTBM = "L01+00000F00";
    public String tradingClearingAccountVTBM = "L01+00002F00";
    public String classCodeVTBM = "TQTF";
    public String instrumentVTBM = tickerVTBM + "_" + classCodeVTBM;

    public String tickerDAL = "DAL";
    public String tradingClearingAccountDAL = "TKCBM_TCAB";
    public String classCodeDAl = "SPBXM";

    public String tickerDD = "DD";
    public String tradingClearingAccountDD = "TKCBM_TCAB";
    public String classCodeDD = "SPBXM";

    public String tickerDOW = "DOW";
    public String tradingClearingAccountDOW = "TKCBM_TCAB";
    public String classCodeDOW = "SPBXM";

    public String tickerINTC = "INTC";
    public String tradingClearingAccountINTC = "TKCBM_TCAB";
    public String classCodeINTC = "SPBXM";

    public String tickerGE = "GE";
    public String tradingClearingAccountGE = "TKCBM_TCAB";
    public String classCodeGE = "SPBXM";

    public String tickerF = "F";
    public String tradingClearingAccountF= "TKCBM_TCAB";
    public String classCodeF = "SPBXM";

    public String tickerGILD = "GILD";
    public String tradingClearingAccountGILD = "TKCBM_TCAB";
    public String classCodeGILD = "SPBXM";

    public String tickerIBM = "IBM";
    public String tradingClearingAccountIBM= "TKCBM_TCAB";
    public String classCodeIBM = "SPBXM";

    public String tickerILMN = "ILMN";
    public String tradingClearingAccountILMN= "TKCBM_TCAB";
    public String classCodeILMN = "SPBXM";

    public String tickerEBAY = "EBAY";
    public String tradingClearingAccountEBAY= "TKCBM_TCAB";
    public String classCodeEBAY = "SPBXM";

    public String tickerINTU = "INTU";
    public String tradingClearingAccountINTU= "TKCBM_TCAB";
    public String classCodeINTU = "SPBXM";

    public String tickerIP = "IP";
    public String tradingClearingAccountIP = "TKCBM_TCAB";
    public String classCodeIP = "SPBXM";

    public String tickerIRM = "IRM";
    public String tradingClearingAccountIRM = "TKCBM_TCAB";
    public String classCodeIRM = "SPBXM";

    public String tickerHUM= "HUM";
    public String tradingClearingAccountHUM = "TKCBM_TCAB";
    public String classCodeHUM = "SPBXM";

    public String tickerRUB000UTSTOM= "RUB000UTSTOM";
    public String tradingClearingAccountRUB000UTSTOM = "MB9885503216";
    public UUID positionIdRUB000UTSTOM = UUID.fromString("33e24a92-aab0-409c-88b8-f2d57415b920");

    public List<String> tradingStatusesFalse = Arrays.asList(
        "not_available_for_trading",
        "trading_closed",
        "break_in_trading",
        "session_close");

    public List<String> tradingStatusesTrue = Arrays.asList(
        "opening_period",
        "opening_auction",
        "closing_period",
        "normal_trading",
        "closing_auction",
        "dark_pool_auction",
        "discrete_auction",
        "trading_at_closing_auction_price",
        "session_open",
        "session_assigned");
}
