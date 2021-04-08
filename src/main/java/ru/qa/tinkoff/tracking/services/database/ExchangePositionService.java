package ru.qa.tinkoff.tracking.services.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.tracking.entities.ExchangePosition;
import ru.qa.tinkoff.tracking.repositories.ExchangePositionRepository;

import java.util.Optional;


@Slf4j
@Service
public class ExchangePositionService {

    final ExchangePositionRepository exchangePositionRepository;
    final ObjectMapper objectMapper;

    public ExchangePositionService(ExchangePositionRepository exchangePositionRepository,
                                   ObjectMapper objectMapper) {
        this.exchangePositionRepository = exchangePositionRepository;
        this.objectMapper = objectMapper;
    }

    @Step("Удаление инструмента")
    @SneakyThrows
    public void deleteExchangePosition(ExchangePosition exchangePosition) {
        Allure.addAttachment("Удаленный инструмент", "application/json", objectMapper.writeValueAsString(exchangePosition));
        exchangePositionRepository.delete(exchangePosition);
        log.info("Successfully deleted client {}", exchangePosition.toString());
    }


    @Step("Поиск интрумента по ticker")
    @SneakyThrows
    public ExchangePosition getExchangePositionByTicker(String ticker, String tradingCleaningAccount) {
        Optional<ExchangePosition> instrument = exchangePositionRepository
            .findExchangePositionByTicker(ticker, tradingCleaningAccount);
        log.info("Successfully find exchangePosition {}", instrument);
        Allure.addAttachment("Найденный инструмент", "application/json", objectMapper.writeValueAsString(instrument));
        return instrument.orElseThrow(() -> new RuntimeException("Не найден инструмент"));
    }


    @Step("Поиск интрумента по ticker")
    @SneakyThrows
    public Optional<ExchangePosition> findExchangePositionByTicker(String ticker, String tradingCleaningAccount) {
        Optional<ExchangePosition> instrument = exchangePositionRepository
            .findExchangePositionByTicker(ticker, tradingCleaningAccount);
        log.info("Successfully find exchangePosition {}", instrument);
        Allure.addAttachment("Найденный инструмент", "application/json", objectMapper.writeValueAsString(instrument));
        return instrument;
    }

    @SneakyThrows
    @Step("Сохранение инструмента")
    public ExchangePosition saveExchangePosition(ExchangePosition exchangePosition)  {
        ExchangePosition saved = exchangePositionRepository.save(exchangePosition);
        log.info("Successfully saved exchangePosition {}", saved);
        Allure.addAttachment("Инструмент", "application/json", objectMapper.writeValueAsString(saved));
        return saved;
    }




}
