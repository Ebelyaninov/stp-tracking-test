package ru.qa.tinkoff.tracking.services.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.tracking.entities.CorpAction;
import ru.qa.tinkoff.tracking.repositories.CorpActionRepository;


import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class CorpActionService {
    final CorpActionRepository corpActionRepository;
    final ObjectMapper objectMapper;

    public CorpActionService(CorpActionRepository corpActionRepository,
                           ObjectMapper objectMapper) {
        this.corpActionRepository = corpActionRepository;
        this.objectMapper = objectMapper;
    }


    @Step("Поиск записи по КД стратегии по id")
    @SneakyThrows
    public List<CorpAction> getCorpAction(UUID strategyId) {
        List<CorpAction> corpAction = corpActionRepository.findCorpActionByContractId(strategyId);
        log.info("Successfully find strategy {}", strategyId);
        Allure.addAttachment("Найденные записи по КД стратегии", "application/json", objectMapper.writeValueAsString(strategyId));
        return corpAction;
    }

}
