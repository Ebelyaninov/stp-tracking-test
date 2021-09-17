package ru.qa.tinkoff.tracking.services.database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.tracking.entities.Strategy;
import ru.qa.tinkoff.tracking.entities.enums.StrategyStatus;
import ru.qa.tinkoff.tracking.repositories.StrategyRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
//*** Методы для работы с таблицами contract & strategy БД postgres@db-tracking.trading.local ***//


@Slf4j
@Service
public class StrategyService {


    final StrategyRepository strategyRepository;
    final ObjectMapper objectMapper;

    public StrategyService(StrategyRepository strategyRepository,
                           ObjectMapper objectMapper) {
        this.strategyRepository = strategyRepository;
        this.objectMapper = objectMapper;
    }


    @Step("Поиск стратегии по id")
    @SneakyThrows
    public Strategy getStrategy(UUID strategyId) {
        Optional<Strategy> strategy = strategyRepository.findById(strategyId);
        log.info("Successfully find strategy {}", strategyId);
        Allure.addAttachment("Найденная стратегия", "application/json", objectMapper.writeValueAsString(strategyId));
        return strategy.orElseThrow(() -> new RuntimeException("Не найдена стратегия"));
    }


    @Step("Поиск стратегий")
    @SneakyThrows
    public List<Strategy> getListStrategies() {
        List<Strategy> strategy = strategyRepository.findAll();
        log.info("Successfully find  list strategy {}");
        Allure.addAttachment("Найденный список стратегий ", "application/json", objectMapper.toString());
        return strategy;
    }


    @Step("Поиск стратегии по идентификатору контракта")
    @SneakyThrows
    public Optional<Strategy> findStrategyByContractId(String contractId) {
        Optional<Strategy> strategy = strategyRepository.findStrategyByContractId(contractId);
        log.info("Successfully find strategy {}", contractId);
        Allure.addAttachment("Найденная стратегия по контракту", "application/json", objectMapper.writeValueAsString(contractId));
        return strategy;
    }


    @Step("Поиск стратегий по идентификатору контракта")
    @SneakyThrows
    public List<Strategy> findListStrategyByContractId(String contractId) {
        List<Strategy> strategy = strategyRepository.findListStrategyByContractId(contractId);
        log.info("Successfully find  list strategy {}", contractId);
        Allure.addAttachment("Найденный список стратегий по контракту", "application/json", objectMapper.writeValueAsString(contractId));
        return strategy;
    }


    @Step("Поиск стратегий по статусу и не пустому значению Description")
    @SneakyThrows
    public List<Strategy> getStrategyByStatus(StrategyStatus status) {
        List<Strategy> strategy = strategyRepository.findStrategyByStatus(status);
        log.info("Successfully find strategy {}", status);
        Allure.addAttachment("Найденная стратегия по статусу", "application/json", objectMapper.writeValueAsString(status));
        return strategy;
    }


    @Step("Поиск стратегий по статусу и не пустому значению Description")
    @SneakyThrows
    public List<Strategy> getStrategyByStatusWithProfile(StrategyStatus status) {
//        List<Strategy> strategy = strategyRepository
//            .findStrategyByStatusWithProfile(status);
        log.info("Successfully find strategy {}");
        Allure.addAttachment("Найденная стратегия по статусу", "application/json",objectMapper.writeValueAsString(status));
        return null;
    }

    @Step("Удаление стратегии")
    @SneakyThrows
    public void deleteStrategy(Strategy strategy) {
        Allure.addAttachment("Удаленная стратегия", "application/json", objectMapper.writeValueAsString(strategy));
        strategyRepository.delete(strategy);
        log.info("Successfully deleted strategy {}", strategy.toString());
    }

    @Step("Удаление стратегии по идентификатору")
    @SneakyThrows
    public void deleteStrategyByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            log.error("Удаление стратегий не выполняется - пустой список идентификаторов стратегий");
        }
        // todo Allure.addAttachment("Удаленная стратегия", "application/json", strategy));
        strategyRepository.deleteStrategiesByIdIn(ids);
        log.info("Successfully deleted strategy {}", ids);
    }


    @Step("Сохранение стратегии")
    public Strategy saveStrategy(Strategy strategy) throws JsonProcessingException {
        Strategy saved = strategyRepository.save(strategy);
        log.info("Successfully saved strategy {}", saved);
        Allure.addAttachment("Стратегия", "application/json", objectMapper.writeValueAsString(saved));
        return saved;
    }


    @Step("Поиск 1 стратегии")
    @SneakyThrows
    public Optional<Strategy> findOneContract() {
        Optional<Strategy> strategy = strategyRepository.selectOneStrategy();
        log.info("Successfully find exchangePosition {}", strategy);
        Allure.addAttachment("Найденная стратегия", "application/json", objectMapper.writeValueAsString(strategy));
        return strategy;
    }

    @Step("Поиск стратегий по статусу и не пустому значению Description")
    @SneakyThrows
    public List<Strategy> getStrategysMaxLimit() {
        List<Strategy> strategy = strategyRepository.selectStrategiesLimit();
        log.info("Successfully find strategys {}");
        return strategy;
    }


    @Step("Поиск стратегий меньше Cursor и с ограничением по лимиту")
    @SneakyThrows
    public List<Strategy> getStrategysByPositionAndLimitmit(Integer position, Integer limit) {
        List<Strategy> strategy = strategyRepository.findListStrategysByPositionAndLimit(position, limit);
        log.info("Successfully find strategys {}", position, limit);
        Allure.addAttachment("Найденные стратегии", "application/json", objectMapper.writeValueAsString(strategy));
        return strategy;
    }

    @Step("Поиск стратегий меньше Cursor и с ограничением по лимиту")
    @SneakyThrows
    public List<Strategy> getStrategysByOrderPosition() {
        List<Strategy> strategy = strategyRepository.findListStrategysByOrderPosition();
        log.info("Successfully find strategys {}");
        Allure.addAttachment("Найденные стратегии", "application/json", objectMapper.writeValueAsString(strategy));
        return strategy;
    }

    @Step("Поиск стратегий по lower(title)")
    @SneakyThrows
    public Strategy getStrategyByLowerTitle (String title) {
        Strategy strategy = strategyRepository.findStrategysByLowerTitle(title);
        log.info("Successfully find strategys {}");
        Allure.addAttachment("По title приведенный к нижнему регистру найденные стратегии", "application/json", objectMapper.writeValueAsString(strategy));
        return strategy;
    }


}