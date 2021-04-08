package ru.qa.tinkoff.kafka.services;

import io.qameta.allure.Step;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.DisposableBean;
import ru.qa.tinkoff.kafka.Topics;

import java.util.Properties;

import static ru.qa.tinkoff.utils.AllureUtils.addTextAttachment;


/**
 * Класс для реализации отправки в Kafka топик сообщений
 */
@Slf4j
public class StringSenderService implements DisposableBean {
    private final KafkaProducer<String, String> kafkaProducer;

    public StringSenderService(final Properties properties) {
        this.kafkaProducer = new KafkaProducer<>(properties);
    }

    @Step("Отправить сообщения в топик {topic.name}")
    public void send(Topics topic, String key, String value) {
        log.info("sending message to topic: {}:\n{}", topic.getName(), value);
        kafkaProducer.send(new ProducerRecord<>(topic.getName(), key, value));
        kafkaProducer.flush();
        addTextAttachment("Сообщение", value);
    }

    @Override
    public void destroy() throws Exception {
        log.info("closing connection");
        kafkaProducer.close();
    }
}