package ru.qa.tinkoff.kafka.services;

import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.kafka.Topics;
import ru.tinkoff.trading.tracking.Tracking;

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangePositionKafkaService {
    private final ProtobufReceiverService<Tracking.ExchangePosition> receiverService;

    @Step("Найти exchangePositions")
    public List<Tracking.ExchangePosition> findReportExecuteByOrdernum() {
        log.info("Получен запрос на на поиск сообщений с номером поручения {} в топике {}",
            Topics.EXCHANGE_POSITION);
        List<Tracking.ExchangePosition> messages = await().atMost(Duration.ofSeconds(20))
            .until(
                () ->
                    receiverService.receiveBatch(Topics.EXCHANGE_POSITION, Duration.ofSeconds(5), Tracking.ExchangePosition.class),
                is(not(empty()))
            );
        log.info("Сообщений с номером поручения {} найдено {} в топике {}", messages.size(),
            Topics.EXCHANGE_POSITION);
        return messages;
    }
}
