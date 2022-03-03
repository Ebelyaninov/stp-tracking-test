package ru.qa.tinkoff.steps.trackingSiebel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


//здесь храним список используемых siebel_id в авто-тестах
@Slf4j
@Service
@RequiredArgsConstructor
public class StpSiebel {

    public String siebelIdMasterAdmin = "5-CQNPKPNH";
    public String siebelIdSlaveAdmin = "5-22NDYVFEE";
    public String siebelIdApiMaster = "4-10YQRW0N";
    public String siebelIdApiSlave = "4-23D6LZT9";
    public String siebelIdApiNotOpen = "5-3MYZ425J";
    public String siebelIdApiNotBroker = "5-DGXUTIXL";
    public String siebelIdMasterStpTrackingMaster = "1-DPVDVIC";
    public String siebelIdSlaveStpTrackingMaster = "1-424AQYT";

    public String siebelIdSlaveActiveStpTrackingMaster = "4-175NOS0W";
    public String siebelIdSlaveBlockedStpTrackingMaster = "4-17XFN163";
    public String siebelIdSlaveMaster = "5-GEKUR6VD";
    public String siebelIdSlaveSlave = "5-JEF71TBN";
}
