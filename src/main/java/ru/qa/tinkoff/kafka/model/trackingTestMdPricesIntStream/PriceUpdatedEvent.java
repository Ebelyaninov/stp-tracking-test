package ru.qa.tinkoff.kafka.model.trackingTestMdPricesIntStream;

import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class PriceUpdatedEvent {
    public static String getKafkaTemplate(LocalDateTime date,  String instrumentId, String priceType, String oldPrice, String newPrice ) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
        String evenId = UUID.randomUUID().toString();
        String createdAt = date.format(formatter) +"Z";
        String ts = date.format(formatter) + "Z";
        String eventText = getResourceAsText("kafkaTemplete/trackingTestMdPricesIntStream/PriceUpdatedEvent.json");
        return eventText
            .replace("$eventId", evenId)
            .replace("$createdAt", createdAt)
            .replace("$instrumentId", instrumentId)
            .replace("$priceType", priceType)
            .replace("$oldPrice", oldPrice)
            .replace("$newPrice", newPrice)
            .replace("$ts", ts);

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
