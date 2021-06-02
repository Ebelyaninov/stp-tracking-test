package ru.qa.tinkoff.kafka.services;

import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Headers;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.kafka.Topics;
import ru.tinkoff.invest.sdet.kafka.prototype.sender.BoostedSender;

import static ru.qa.tinkoff.utils.AllureUtils.addTextAttachment;


/**
 * Класс для реализации отправки в Kafka топик сообщений
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StringSenderService {

    private final BoostedSender<String, String> boostedSender;

    @Step("Отправить сообщения в топик {topic.name}")
    public void send(Topics topic, String key, String value) {
        log.info("sending message to topic: {}:\n{}", topic.getName(), value);
        boostedSender.send(topic.getName(), key, value);
        addTextAttachment("Сообщение", value);
    }

    @Step("Отправить сообщения в топик {topic.name}")
    public void send(Topics topic, String key, String value, Headers headers) {
        log.info("sending message to topic: {}:\n{}", topic.getName(), value);
        boostedSender.send(topic.getName(), key, value, headers);
        addTextAttachment("Сообщение", value);
    }

}