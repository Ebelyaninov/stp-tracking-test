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


}
