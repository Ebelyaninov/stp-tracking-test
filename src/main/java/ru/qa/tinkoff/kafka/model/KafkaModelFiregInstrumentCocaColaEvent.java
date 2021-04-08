package ru.qa.tinkoff.kafka.model;

import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class KafkaModelFiregInstrumentCocaColaEvent {

    String updateAt;


    public static String getKafkaTemplate(LocalDateTime updatedAt, String lot) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
        String updatedAtString = updatedAt.format(formatter) + "000+03:00";
        String eventText = getResourceAsText("kafkaTemplete/firegInstrument/CocaCola.json");
        String newEventText = eventText.replace("%s", updatedAtString);
        return newEventText.replace("%b", lot);
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

    KafkaModelFiregInstrumentCocaColaEvent updateAt(String updateAt) {
        this.updateAt = updateAt;
        return this;
    }



    @Override
    public String toString() {
        return "KafkaModelFiregInstrumentCocaColaEvent{" +
            "updateAt='" + updateAt + '\'' +
            '}';
    }



}
