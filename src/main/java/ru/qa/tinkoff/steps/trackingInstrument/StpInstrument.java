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

    public String tickerAEE1 = "AEE1";
    public String tradingClearingAccountAEE1 = "L01+00000BLP";
    public String classCodeAEE1 = "SPBXM";
    public String instrumentAEE1 = tickerAEE1 + "_" + classCodeAEE1;

    public String tickerAAPL = "AAPL";
    public String tradingClearingAccountAAPL = "TKCBM_TCAB";
    public UUID positionIdAAPL = UUID.fromString("5c5e6656-c4d3-4391-a7ee-e81a76f1804e");
    public UUID assetUidAAPL = UUID.fromString("df2480ba-ddc0-4bea-b1be-2a42f9617b6e");
    public UUID positionUIDAAPL = UUID.fromString("a9eb4238-eba9-488c-b102-b6140fd08e38");
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
    public UUID assetUidABBV = UUID.fromString("de197d08-c63f-4674-a150-49f5962b8dff");
    public String classCodeABBV = "SPBXM";
    public String instrumentABBV = tickerABBV + "_" + classCodeABBV;

    public String tickerSBER = "SBER";
    public String tradingClearingAccountSBER = "L01+00002F00";
    public String tradingClearingAccountSBER1 = "L01+00000F00";
    public UUID positionIdSBER = UUID.fromString("41eb2102-5333-4713-bf15-72b204c4bf7b");
    public UUID assetUidSBER = UUID.fromString("40d89385-a03a-4659-bf4e-d3ecba011782");
    public UUID positionUIDSBER = UUID.fromString("e6123145-9665-43e0-8413-cd61b8aa9b13");
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
    public UUID assetUidFB = UUID.fromString("ad737f70-1581-4b99-abd9-631fa3096d95");
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
    public UUID assetUidNOK = UUID.fromString("d837a953-de96-430f-9eee-2a24ae9608ff");



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
    public UUID positionIdSU29009RMFS6 = UUID.fromString("bcf000c7-03c4-4cb7-b79d-892a881b1d29");

    public String tickerLKOH = "LKOH";
    public String tradingClearingAccountLKOH = "L01+00002F00";
    public String classCodeLKOH = "TQBR";
    public String sectorLKOH = "energy";
    public String typeLKOH = "share";
    public String companyLKOH = "Лукойл";
    public String instrumentLKOH = tickerLKOH + "_" + classCodeLKOH;


    public String tickerSNGSP = "SNGSP";
    public String tradingClearingAccountSNGSP = "L01+00002F00";
    public String classCodeSNGSP = "TQBR";
    public String sectorSNGSP = "energy";
    public String typeSNGSP = "share";
    public String companySNGSP = "Сургутнефтегаз";
    public String instrumentSNGSP = tickerSNGSP + "_" + classCodeSNGSP;


    public String tickerTRNFP = "TRNFP";
    public String tradingClearingAccountTRNFP = "L01+00002F00";
    public String classCodeTRNFP = "TQBR";
    public String sectorTRNFP = "energy";
    public String typeTRNFP = "share";
    public String companyTRNFP = "Транснефть";
    public String instrumentTRNFP = tickerTRNFP + "_" + classCodeTRNFP;


    public String tickerESGR = "ESGR";
    public String tradingClearingAccountESGR = "L01+00002F00";
    public String classCodeESGR = "TQTF";
    public String sectorESGR = "other";
    public String typeESGR = "etf";
    public String companyESGR = "РСХБ Управление Активами";
    public String instrumentESGR = tickerESGR + "_" + classCodeESGR;


    public String tickerYNDX = "YNDX";
    public String tradingClearingAccountYNDX = "L01+00002F00";
    public UUID positionIdYNDX = UUID.fromString("cb51e157-1f73-4c62-baac-93f11755056a");
    public UUID positionUIDYNDX = UUID.fromString("10e17a87-3bce-4a1f-9dfc-720396f98a3c");
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
    public UUID positionIdEURRUB = UUID.fromString("2415fb51-2ebe-4669-8ea4-9ba09e765366");

    public String tickerXS0587031096 = "XS0587031096";
    public String classCodeXS0587031096 = "RPEU";
    public String tradingClearingAccountXS0587031096 = "L01+00000SPB";
    public UUID assetUidXS0587031096 = UUID.fromString("3b3dea9b-fb02-44c4-bf2a-2750ae4147d9");
    public UUID instrumentId2XS0587031096 = UUID.fromString("0df2c5c3-b30c-4777-b666-ed70b64bd714");


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
    public UUID positionUIDALFAperp = UUID.fromString("f179f0f0-ec6e-4f9f-9980-3c033ee248d1");
    public String classCodeALFAperp = "SPBBND";
    public String instrumentALFAperp = tickerALFAperp + "_" + classCodeALFAperp;


    public String tickerXS1589324075 = "XS1589324075";
    public String classCodeXS1589324075 = "TQOD";
    public String tradingClearingAccountXS1589324075 = "L01+00000F00";


    public String tickerSBERT = "SBERT";
    public String tradingClearingAccountSBERT = "L01+00002F00";
    public String classCodeSBERT = "TQBR";
    //Не нахожу инструмент
    public UUID positionIdSBERT = UUID.fromString("5c5e6656-c4d3-4391-a7ee-e81a76f1804e");


    public String tickerAFX = "AFX@DE";
    public String tradingClearingAccountAFX = "L01+00000SPB";
    public String classCodeAFX = "SPBDE";
    public UUID positionIdAFX = UUID.fromString("94676acd-3c13-4e24-8ad1-c8795561b471");


    public String tickerTCSG = "TCSG";
    public String tradingClearingAccountTCSG = "L01+00000F00";
    public String classCodeTCSG = "TQBR";
    public UUID positionIdTCSG = UUID.fromString("4a1946eb-5ffb-4ebf-ac8c-44221c9f7a2f");

    public String tickerTBIO = "TBIO";
    public String tradingClearingAccountTBIO = "NDS000000001";
    public String classCodeTBIO = "TQTF";
    public UUID positionIdTBIO = UUID.fromString("a1182dcf-b59a-4175-bfed-ab6e11a6e00b");

    public String tickerUSDRUB = "USDRUB";
    public String tradingClearingAccountUSDRUB = "MB9885503216";
    public String classCodeUSDRUB = "EES_CETS";
    public String instrumentUSDRUB = tickerUSDRUB + "_" + classCodeUSDRUB;
    public UUID positionIdUSDRUB = UUID.fromString("6e97aa9b-50b6-4738-bce7-17313f2b2cc2");

    public String tickerGBP = "GBPRUB";
    public String tradingClearingAccountGBP = "MB9885503216";
    public String classCodeGBP = "EES_CETS";
    public String instrumentGBP = tickerGBP + "_" + classCodeGBP;
    public UUID positionIdGBP = UUID.fromString("f1ecc477-ec8b-40e9-ab47-7706ac56a935");

    public String tickerJPY = "JPYRUB";
    public String tradingClearingAccountJPY = "MB9885503216";

    public String tickerRUB = "RUB000UTSTOM";
    public String tradingClearingAccountRUB = "MB9885503216";


    public String tickerCHF = "CHFRUB";
    public String tradingClearingAccountCHF = "MB9885503216";
    public String classCodeCHF = "EES_CETS";
    public UUID positionIdCHF = UUID.fromString("282725e9-69f5-4bc9-a300-f02ccedbafb2");

    public String tickerHKD = "HKDRUB";
    public String tradingClearingAccountHKD = "MB9885503216";
    public String classCodeHKD = "EES_CETS";
    public String instrumentHKD = tickerHKD + "_" + classCodeHKD;
    public UUID positionIdHKD = UUID.fromString("de34b358-b063-4558-88ab-b573dff8841f");

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
    public String tradingClearingAccountBCR = "TKCBM_TCAB";
    public UUID positionIdBCR = UUID.fromString("10fab6fa-8ad2-4c42-97d6-e7a1c02036fb");

    public String tickerBRJ1 = "BRJ1";
    public String classCodeBRJ1 = "SPBFUT";
    public String tradingClearingAccountBRJ1 = "TB00";


    public String tickerQCOM = "QCOM";
    //public String tradingClearingAccountQCOM = "L01+00000SPB";
    public String tradingClearingAccountQCOM = "TKCBM_TCAB";
    public String classCodeQCOM = "SPBXM";
    public UUID positionIdQCOM = UUID.fromString("4e614863-4656-4e4d-be0b-cb66d76e80d1");

    public String tickerTUSD = "TUSD";
    public String tradingClearingAccountTUSD = "L01+00000F00";
    public String classCodeTUSD = "TQTD";
    public String instrumentTUSD = tickerTUSD + "_" + classCodeTUSD;
    public UUID positionIdTUSD = UUID.fromString("8ccf8aee-3cdf-47e3-b8ad-737900ea64c0");

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
    public UUID positionIdGLDRUB = UUID.fromString("cf4fde41-c553-415c-82a6-cd2cdffd5e27");

    public String tickerKZT = "KZTRUB_TOM";
    public String tradingClearingAccountKZT = "MB9885503216";
    public String classCodeKZT = "CETS";
    public UUID positionIdKZT = UUID.fromString("6a6c93fc-a954-475f-96ed-2d8128aaaf56");

    public String tickerBYN = "BYNRUB_TOM";
    public String tradingClearingAccountBYN = "MB9885503216";
    public String classCodeBYN = "CETS";
    public UUID positionIdBYN = UUID.fromString("558a8c0b-9d68-4da5-a375-9be773ea32b0");

    public String tickerXAU = "GLDRUB_TOM";
    public String tradingClearingAccountXAU = "MB9885503216";
    public String classCodeXAU = "CETS";
    public UUID positionIdXAU = UUID.fromString("cf4fde41-c553-415c-82a6-cd2cdffd5e27");

    public String tickerXAG = "SLVRUB_TOM";
    public String tradingClearingAccountXAG = "MB9885503216";
    public String classCodeXAG = "CETS";
    public UUID positionIdXAG = UUID.fromString("9a68d2b1-0967-4ec2-808c-fa9071fe654e");

    public String tickerMTS0620 = "MTS0620";
    public String tradingClearingAccountMTS0620 = "L01+00000SPB";
    public String classCodeMTS0620 = "SPBBND";

    public String tickerCCL = "CCL";
    public String tradingClearingAccountCCL = "TKCBM_TCAB";
    public String classCodeCCL = "SPBXM";
    public UUID positionIdCCL = UUID.fromString("3deba42b-4abd-4eaf-bc42-077981a26821");

    public String tickerSTM = "STM";
    public String classCodeSTM = "STM";
    public String tradingClearingAccountSTM = "NDS000000001";
    public UUID   positionIdSTM = UUID.fromString("0a0e23ad-efe1-4d35-b59b-aa9cb718d6eb");
    public UUID   assetUidSTM = UUID.fromString("96de9ec5-e8de-4450-a045-c054000a3552");

    public String tickerLNT = "LNT";
    public String classCodeLNT = "SPBXM";
    public String tradingClearingAccountLNT = "L01+00000SPB";
    public UUID positionIdLNT = UUID.fromString("69a8b572-008d-4b22-bdaa-e56b7cd740bc");
    public UUID assetUidLNT = UUID.fromString("e037babe-6dcf-4a30-ba70-bed9945a8abf");

    public String tickerVTBM = "VTBM";
    //public String tradingClearingAccountVTBM = "L01+00000F00";
    public String tradingClearingAccountVTBM = "L01+00002F00";
    public String classCodeVTBM = "TQTF";
    public String instrumentVTBM = tickerVTBM + "_" + classCodeVTBM;
    public UUID positionIdVTBM = UUID.fromString("eee36ccf-5f28-4419-9c29-c6465f39581a");
    public UUID positionUIDBTBM = UUID.fromString("ade12bc5-07d9-44fe-b27a-1543e05bacfd");

    public String tickerDAL = "DAL";
    public String tradingClearingAccountDAL = "TKCBM_TCAB";
    public String classCodeDAl = "SPBXM";
    public UUID positionIdDAL = UUID.fromString("2ce6103e-54b1-4178-a4ba-b36fdcf300cd");

    public String tickerDD = "DD";
    public String tradingClearingAccountDD = "TKCBM_TCAB";
    public String classCodeDD = "SPBXM";
    public UUID positionIdDD = UUID.fromString("041de167-4a78-4327-92ae-49e73c7d97c2");

    public String tickerDOW = "DOW";
    public String tradingClearingAccountDOW = "TKCBM_TCAB";
    public String classCodeDOW = "SPBXM";
    public UUID positionIdDOW = UUID.fromString("4f284479-72eb-4338-b1ae-6c5d471ad354");

    public String tickerINTC = "INTC";
    public String tradingClearingAccountINTC = "TKCBM_TCAB";
    public String classCodeINTC = "SPBXM";
    public UUID positionIdINTC = UUID.fromString("cb6b0d66-181b-4462-ade8-486754b3afe7");

    public String tickerGE = "GE";
    public String tradingClearingAccountGE = "TKCBM_TCAB";
    public String classCodeGE = "SPBXM";
    public UUID positionIdGE = UUID.fromString("1db3eaba-57ae-4e87-877f-b9f368651525");

    public String tickerF = "F";
    public String tradingClearingAccountF= "TKCBM_TCAB";
    public String classCodeF = "SPBXM";
    public UUID positionIdF = UUID.fromString("78f759ed-65ce-4611-80c4-0afb5d92cbbd");

    public String tickerGILD = "GILD";
    public String tradingClearingAccountGILD = "TKCBM_TCAB";
    public String classCodeGILD = "SPBXM";
    public UUID positionIdGILD = UUID.fromString("ec562b29-899f-4c8f-b289-a14144f81935");

    public String tickerIBM = "IBM";
    public String tradingClearingAccountIBM= "TKCBM_TCAB";
    public String classCodeIBM = "SPBXM";

    public String tickerILMN = "ILMN";
    public String tradingClearingAccountILMN= "TKCBM_TCAB";
    public String classCodeILMN = "SPBXM";
    public UUID positionIdILMN = UUID.fromString("25b1bb14-db79-44a2-b32f-620ce7bac850");

    public String tickerEBAY = "EBAY";
    public String tradingClearingAccountEBAY= "TKCBM_TCAB";
    public String classCodeEBAY = "SPBXM";
    public UUID positionIdEBAY = UUID.fromString("f9083350-b9e5-400f-879e-e44a6b439547");

    public String tickerINTU = "INTU";
    public String tradingClearingAccountINTU= "TKCBM_TCAB";
    public String classCodeINTU = "SPBXM";
    public UUID positionIdINTU = UUID.fromString("2b8a30d5-15db-494f-a8f4-64048197f77d");

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

    public String tickerTEUR= "TEUR";
    public String tradingClearingAccountTEUR = "L01+00002F00";
    public UUID positionIdTEUR = UUID.fromString("a9a9d608-c77f-4a9b-8b1c-ef791cd9926f");

    public String tickerSKX = "SKX";
    public String classCodeSKX = "SPBXM";
    public String tradingClearingAccountSKX = "TKCBM_TCAB";
    public UUID positionIdSKX = UUID.fromString("98d32a7a-0e9a-4ecd-860e-ebbb850e42d2");

    public String tickerAMDRUB = "AMDRUB_TOM";
    public String tradingClearingAccountAMDRUB = "MB9885503216";
    public String classCodeAMDRUB = "CETS";
    public String instrumentAMDRUB = tickerAMDRUB + "_" + classCodeAMDRUB;
    public UUID positionIdAMDRUB = UUID.fromString("4515b482-71f0-4193-aa0a-b91efdab7062");


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
