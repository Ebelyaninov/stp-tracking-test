package ru.qa.tinkoff.steps.trackingSiebel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


//здесь храним список используемых siebel_id в авто-тестах
@Slf4j
@Service
@RequiredArgsConstructor
public class StpSiebel {

    // "5-GA3OLBWB" "1-7XOAYPX" "4-LQB8FKN" "5-23AZ65JU2" "5-DYNN1E3S"
    // "5-4LCY1YEB" "5-JEF71TBN" "4-1UBHYQ63" "1-1XHHA7S"

    // Admin //
    public String siebelIdMasterAdmin = "5-55RUONV5";
    public String siebelIdSlaveAdmin = "5-22NDYVFEE";

    public String siebelIdAdmin = "5-55RUONV5";
    public String siebelIdEmptyNick = "4-1OJEJVLA";
    public String siebelIdNullImage = "5-IB7NPAPB";
    public String siebelIdNotBroker = "5-11FZVG5DZ";
    public String siebelIdNotOpen = "5-5AV7MQT1";


    // API //
    public String siebelIdApiMaster = "4-10YQRW0N";
    public String siebelIdApiSlave = "4-23D6LZT9";
    public String siebelIdApiNotOpen = "5-3MYZ425J";
    public String siebelIdApiNotBroker = "5-DGXUTIXL";
    public String siebelIdMasterStpTrackingMaster = "1-DPVDVIC";
    public String siebelIdSlaveStpTrackingMaster = "4-13TDRO5W";
    public String siebelIdSlaveActiveStpTrackingMaster = "1-54633V2";
    public String siebelIdSlaveBlockedStpTrackingMaster = "4-17XFN163";

    // Fee //
    public String siebelIdMasterStpTrackingFee = "5-F0XGQDPV";
    public String siebelIdSlaveStpTrackingFee = "4-IJ8NBW3";

    // Analytics //
    public String siebelIdMasterAnalytics = "5-192WBUXCI";
    public String siebelIdMasterAnalytics1 = "5-AJ7L9FNI";

    public String siebelIdAnalyticsSlaveOne = "5-1P87U0B13";
    public String siebelIdAnalyticsSlaveTwo = "5-7ECGV169";
    public String siebelIdAnalyticsSlaveThree = "5-3CGSIDQR";

    //StpTrackingSlave// 5-528RJAU5
    public String siebelIdSlaveMaster = "4-LEJHC6T";
    public String siebelIdSlaveSlave = "5-JEF71TBN";
    public String siebelIdSlaveGRPC = "5-IQGHZUOO";

    public String siebelIdAnalyzeMaster ="1-3Z0IR7O";
    public String siebelIdAnalyzeSlave = "1-BXDUEON";
    public String siebelIdAnalyzeSlaveOnlyBaseMoney = "1-1AJ30Q";
    public String siebelIdAnalyzeSlaveMoneyAndAAPL = "5-8NL1RLT1";
    public String siebelIdAnalyzeMasterError = "5-2383868GN";
    public String siebelIdAnalyzeSlaveError = "4-LQB8FKN";
    public String siebelIdSlaveOrderSlave = "5-23LMDXSIW";

    public String siebelIdSlaveOrder = "5-JEF71TBN";
    public String siebelIdMasterOrder = "4-1UBHYQ63";



    //socialTrackingClient//
    public String siebelIdMasterForClient = "5-8IZ6ZM9X";
    public String siebelIdSlaveForClient = "5-8NL1RLT1";
    //Для тестов важен ответ от метода getInvestProfile с нужным риск профилем
    public String siebelIdAgressiveForClient = "5-775DOBIB";
    public String siebelIdMediumForClient = "5-4HSBIRY7";
    public String siebelIdConservativeForClient = "5-LGGA88YZ";

    // stpTrackingRetryer //
    public String siebelMasterRetryer = "5-5CNFBO6Z";
    public String siebelSlaveRetryer = "5-LZ9SSTLK";

    public String siebelMasterRetryer1 = "4-1V1UVPX8";

    // handleSocialEvent //
    public String siebelSocial = "1-9JNCS7D"; // необходим не пустой socialProfile


    // Fee
    public String siebelMasterFee = "1-51Q76AT";
    public String siebelMasterSlave = "5-1P87U0B13";

    // socialTrackingNotification //
    public String siebelIdSocialTrackingNotification = "5-4H607YXZ";
}
