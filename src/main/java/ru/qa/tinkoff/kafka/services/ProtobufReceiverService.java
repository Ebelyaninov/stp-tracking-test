package ru.qa.tinkoff.kafka.services;

import com.google.protobuf.GeneratedMessageV3;
import io.qameta.allure.Step;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.qa.tinkoff.kafka.Topics;
import ru.qa.tinkoff.utils.AllureUtils;
import ru.tinkoff.invest.sdet.kafka.protobuf.reciever.KafkaProtobufBytesReceiver;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.List;

import static ru.qa.tinkoff.utils.AllureUtils.addTextAttachment;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProtobufReceiverService<V extends GeneratedMessageV3> {

    @Resource(name = "bytesReceiverFactory")
    KafkaProtobufBytesReceiver<String, V> receiver;

    @Step("Получить сообщения из Kafka топика {topic.name}")
    public List<V> receiveBatch(Topics topic, Duration duration, Class<V> messageClass) {
        log.info("Полечен запрос на получение из Kafka топика {} сообщений {}", topic.getName(), messageClass.getName());
        List<V> result = receiver.receiveBatch(topic.getName(), duration, messageClass);
        log.info("Прочитано из {} сообщений: {}", topic.getName(), result.size());
        addTextAttachment(AllureUtils.FOUND_ENTITIES, result);
        return result;
    }
}
