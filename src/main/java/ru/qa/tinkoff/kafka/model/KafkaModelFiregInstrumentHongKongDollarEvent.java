package ru.qa.tinkoff.kafka.model;

import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class KafkaModelFiregInstrumentHongKongDollarEvent {
    public static String getKafkaTemplate(LocalDateTime updatedAt, String risklevel) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
        String updatedAtString = updatedAt.format(formatter) + "000+03:00";
        String eventText = getResourceAsText("kafkaTemplete/firegInstrument/HongKongDollar.json");
        String newEventText = eventText.replace("%s", updatedAtString);
        return newEventText.replace("%b", risklevel);
    }


    @SneakyThrows
    static String getResourceAsText(String path) {
        try (InputStream inputStream =
                 KafkaModelFiregInstrumentCocaColaEvent.class.getClassLoader().getResourceAsStream(path)) {
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
