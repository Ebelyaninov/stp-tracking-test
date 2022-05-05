package ru.qa.tinkoff.steps.trackingInstrument;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

//здесь храним список используемых инструментов в авто-тестах
@Slf4j
@Service
@RequiredArgsConstructor
public class StpInstrument {

    public String tickerAAPL = "AAPL";
    public String tradingClearingAccountAAPL = "TKCBM_TCAB";
//    public String tradingClearingAccountAAPL1 = "L01+00000SPB";
    public String typeAAPL = "share";
    public String classCodeAAPL = "SPBXM";
    public String briefNameAAPL = "Apple";
    public String imageAAPL = "US0378331005.png";
    public String currencyAAPL = "usd";
    public String instrumentAAPL = tickerAAPL + "_" + classCodeAAPL;


    public String tickerABBV = "ABBV";
    public String tradingClearingAccountABBV = "TKCBM_TCAB";
    public String classCodeABBV = "SPBXM";
    public String instrumentABBV = tickerABBV + "_" + classCodeABBV;

    public String tickerSBER = "SBER";
    public String tradingClearingAccountSBER = "L01+00002F00";
    public String tradingClearingAccountSBER1 = "L01+00000F00";
    public String sectorSBER = "financial";
    public String typeSBER = "share";
    public String companySBER = "Сбербанк";
    public String classCodeSBER = "TQBR";
    public String instrumentSBER = tickerSBER + "_" + classCodeSBER;
    public String briefNameSBER ="Сбер Банк";
    public String imageSBER ="sber.png";


    public String tickerFB = "FB";
    public String tradingClearingAccountFB = "TKCBM_TCAB";
    public String typeFB = "share";
    public String classCodeFB = "SPBXM";
    public String briefNameFB = "Meta Platforms";
    public String imageFB = "000000.png";
    public String currencyFB = "usd";
    public String instrumentFB = tickerFB + "_" + classCodeFB;

    public String tickerNOK = "NOK";
    public String classCodeNOK = "SPBXM";
    public String tradingClearingAccountNOK = "L01+00000SPB";
    public String briefNameNOK = "Nokia";
    public String imageNOK = "US6549022043.png";
    public String typeNOK = "share";


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
    public String tradingClearingAccountXS0191754729 = "L01+00000F00";
    public String typeXS0191754729 = "bond";
    public String classCodeXS0191754729 = "TQOD";
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
//    public String tradingClearingAccountYNDX = "Y02+00001F00";
    public String classCodeYNDX = "TQBR";
    public String sectorYNDX = "telecom";
    public String typeYNDX = "share";
    public String companyYNDX = "Яндекс";
    public String instrumentYNDX = tickerYNDX + "_" + classCodeYNDX;


    public String tickerUSD = "USD000UTSTOM";
    public String tradingClearingAccountUSD = "MB9885503216";
    public String classCodeUSD = "CETS";
    public String sectorUSD = "money";
    public String typeUSD = "money";
    public String companyUSD = "Денежные средства";
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
    public String tradingClearingAccountEURRUB = "MB0253214128";
    public String classCodeEURRUB = "EES_CETS";


    public String tickerXS0587031096 = "XS0587031096";
    public String classCodeXS0587031096 = "RPEU";
    public String tradingClearingAccountXS0587031096 = "L01+00000SPB";

    public String tickerTRUR = "TRUR";
    public String classCodeTRUR = "TQTF";
    public String tradingClearingAccountTRUR = "L01+00000F00";


    public String tickerXS0424860947 = "XS0424860947";
    public String classCodeXS0424860947 = "TQOD";
    public String tradingClearingAccountXS0424860947 = "L01+00000F00";


    public String tickerALFAperp = "ALFAperp";
    public String tradingClearingAccountALFAperp = "TKCBM_TCAB";
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

    public String tickerBCR = "BCR";
    public String classCodeBCR = "SPBXM";
    public String tradingClearingAccountBCR = "L01+00000SPB";

    public String tickerBRJ1 = "BRJ1";
    public String classCodeBRJ1 = "SPBFUT";
    public String tradingClearingAccountBRJ1 = "U800";


    public String tickerQCOM = "QCOM";
    public String tradingClearingAccountQCOM = "L01+00000SPB";
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


}
