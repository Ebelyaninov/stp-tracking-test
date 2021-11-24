package ru.qa.tinkoff.kafka.model.CCYEV;

import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CcyevEvent {

    public static String getKafkaTemplate(String action, LocalDateTime createdAtDate, String amount,
                                          String currency, LocalDateTime entryAtDate, String operCode,
                                          String operId, String contract, LocalDateTime valueDateDate ) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        DateTimeFormatter formatterDate = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
        String createdAtD= createdAtDate.format(formatterDate);
        String createdAt = createdAtDate.format(formatter);
        String entryAt = entryAtDate.format(formatter);
        String valueDate = valueDateDate.format(formatter);

        String eventText = getResourceAsText("kafkaTemplete/CCYEV/handleAdjustEvent.xml");
        return eventText
            .replace("$createdAtDate", createdAtD)
            .replace("$action", action)
            .replace("$createdAt", createdAt)
            .replace("$amount", amount)
            .replace("$currency", currency)
            .replace("$entryAt", entryAt)
            .replace("$operCode", operCode)
            .replace("$operId", operId)
            .replace("$contract", contract)
            .replace("$valueDate", valueDate);

    }



    @SneakyThrows
    static String getResourceAsText(String path) {
        try (InputStream inputStream =
                 CcyevEvent.class.getClassLoader().getResourceAsStream(path)) {
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
