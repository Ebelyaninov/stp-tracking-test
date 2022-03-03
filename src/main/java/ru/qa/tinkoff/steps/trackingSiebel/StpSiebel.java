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
    // "5-4LCY1YEB" "5-JEF71TBN" "4-1UBHYQ63" "1-1XHHA7S" "5-JDFC5N71"

    // Admin //

    public String siebelIdMasterAdmin = "5-CQNPKPNH";
    public String siebelIdSlaveAdmin = "5-22NDYVFEE";

    public String siebelIdAdmin = "5-55RUONV5";
    public String siebelIdEmptyNick = "1-9X6NHTJ";
    public String siebelIdNullImage = "5-421S5P27";
    public String siebelIdNotBroker = "5-11FZVG5DZ";
    public String siebelIdNotOpen = "5-5AV7MQT1";


    // API //

    public String siebelIdApiMaster = "4-10YQRW0N";
    public String siebelIdApiSlave = "4-23D6LZT9";
    public String siebelIdApiNotOpen = "5-3MYZ425J";
    public String siebelIdApiNotBroker = "5-DGXUTIXL";
    public String siebelIdMasterStpTrackingMaster = "1-DPVDVIC";
    public String siebelIdSlaveStpTrackingMaster = "1-424AQYT";

    public String siebelIdSlaveActiveStpTrackingMaster = "4-175NOS0W";
    public String siebelIdSlaveBlockedStpTrackingMaster = "4-17XFN163";

    // Analytics //

    public String siebelIdMasterAnalytics = "5-192WBUXCI";
    public String siebelIdMasterAnalytics1 = "5-AJ7L9FNI";

    public String siebelIdAnalyticsSlaveOne = "5-1P87U0B13";
    public String siebelIdAnalyticsSlaveTwo = "5-7ECGV169";
    public String siebelIdAnalyticsSlaveThree = "5-3CGSIDQR";

    //StpTrackingSlave//
    public String siebelIdSlaveMaster = "5-GEKUR6VD";
    public String siebelIdSlaveSlave = "5-JEF71TBN";

    //socialTrackingClient//
    public String siebelIdMasterForClient = "4-1P767GFO";
    public String siebelIdSlaveForClient = "5-8NL1RLT1";
    //Для тестов важен ответ от метода getInvestProfile с нужным риск профилем
    public String siebelIdAgressiveForClient = "5-775DOBIB";
    public String siebelIdMediumForClient = "5-4HSBIRY7";
    public String siebelIdConservativeForClient = "5-LGGA88YZ";

}
