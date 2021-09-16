package ru.qa.tinkoff.kafka.oldkafkaservice;

import io.qameta.allure.Step;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Headers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.kafka.Topics;
import ru.tinkoff.invest.sdet.kafka.prototype.sender.BoostedSender;

import static ru.qa.tinkoff.utils.AllureUtils.addTextAttachment;

@Service
@Slf4j
public class OldKafkaService {
    @Autowired
    @Qualifier("oldKafkaByteToByteSender")
    private BoostedSender<byte[], byte[]> kafkaByteToByteSender;
    @Autowired
    @Qualifier("oldKafkaByteArraySender")
    private BoostedSender<String, byte[]> kafkaByteArraySender;



    @Step("Отправить сообщения в топик {topic.name}")
    public void send(Topics topic, byte[] key, byte[] value) {
        log.info("sending message to topic: {}:\n{}", topic.getName(), value);
        kafkaByteToByteSender.send(topic.getName(), key, value);
        addTextAttachment("Сообщение", value);
    }


    @Step("Отправить сообщения в топик {topic.name}")
    public void send(Topics topic, String key, byte[] value) {
        log.info("sending message to topic: {}:\n{}", topic.getName(), value);
        kafkaByteArraySender.send(topic.getName(), key, value);
        addTextAttachment("Сообщение", value);
    }

    @Step("Отправить сообщения в топик {topic.name}")
    public void send(Topics topic, String key, byte[] value, Headers headers) {
        log.info("sending message to topic: {}:\n{}", topic.getName(), value);
        kafkaByteArraySender.send(topic.getName(), key, value, headers);
        addTextAttachment("Сообщение", value);
    }
}
