package ru.qa.tinkoff.tracking.services.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.info.ProjectInfoProperties;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.tracking.entities.Client;
import ru.qa.tinkoff.tracking.entities.CorpAction;
import ru.qa.tinkoff.tracking.entities.Dividend;
import ru.qa.tinkoff.tracking.repositories.CorpActionRepository;
import ru.qa.tinkoff.tracking.repositories.DividendRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
@Slf4j
@Service
public class DividendService {
    final DividendRepository dividendRepository;
    final ObjectMapper objectMapper;

    public DividendService(DividendRepository dividendRepository,
                             ObjectMapper objectMapper) {
        this.dividendRepository = dividendRepository;
        this.objectMapper = objectMapper;
    }

    @Step("Поиск дивиденда по стратегии")
    @SneakyThrows
    public List<Dividend> getDividend(UUID strategyId) {
        List<Dividend> dividend = dividendRepository.findDividendByStrategyId(strategyId);
        log.info("Successfully find dividend by strategy {}", strategyId);
        Allure.addAttachment("Найденные записи по дивиденду", "application/json", objectMapper.writeValueAsString(strategyId));
        return dividend;
    }

    @Step("Поиск дивиденда по стратегии")
    @SneakyThrows
    public void deleteAllDividendsByStrategyId(UUID strategyId) {
        List<Dividend> dividend = dividendRepository.findDividendByStrategyId(strategyId);
        log.info("Successfully find dividend by strategy {}", strategyId);
        Allure.addAttachment("Найденные записи по дивиденду", "application/json", objectMapper.writeValueAsString(strategyId));
        dividendRepository.deleteInBatch(dividend);
    }

    @Step("Добавляем запись в dividend")
    @SneakyThrows
    public void insertIntoDividend(Long id , UUID strategyId, Timestamp createdAt) {
        Dividend dividend = new Dividend()
            .setId(id)
            .setStrategyId(strategyId)
            .setCreatedAt(createdAt);
        dividendRepository.save(dividend);
        log.info("Successfully created dividend {}", dividend.toString());
    }
}
