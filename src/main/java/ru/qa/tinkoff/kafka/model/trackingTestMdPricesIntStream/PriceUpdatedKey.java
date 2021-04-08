package ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream;

import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class PriceUpdatedKey {
    public static String getKafkaTemplate(String instrumentId) {
        String eventText = getResourceAsText("kafkaTemplete/trackingTestMdPricesIntStream/PriceUpdatedKey.json");
        return eventText.replace("$instrumentId", instrumentId);


    }


    @SneakyThrows
    static String getResourceAsText(String path) {
        try (InputStream inputStream =
                 PriceUpdatedEvent.class.getClassLoader().getResourceAsStream(path)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            buffer.flush();
            byte[] byteArray = buffer.toByteArray();

            return new String(byteArray, StandardCharsets.UTF_8);
        }

    }
}
