package ru.qa.tinkoff.tracking.services.allure;

import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.trading.tracking.Tracking;


import static io.qameta.allure.Allure.step;

@Slf4j
@Service
public class AllureMasterSteps {

    @Step("Вернули команду handleActualizeCommand.SaveDevidend: \n {command}")
    public void stepForPortfolioCommand (String stepName, Tracking.PortfolioCommand command){
        step(stepName);
        log.info("Команда handleActualizeCommand.SaveDevidend:  \n {}", command);
    }

}
