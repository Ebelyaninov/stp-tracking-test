package ru.qa.tinkoff.mocks.model;

import lombok.SneakyThrows;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TextResourceEnhancer {

    @SneakyThrows
    public static String enhance(TextResourceInfo textResourceInfo) {
        String text = getResourceAsText(textResourceInfo.path());
        for (var entry : textResourceInfo.params().entrySet()) {
            text = text.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return text;
    }

    private static String getResourceAsText(String path) throws IOException {
        File file = ResourceUtils.getFile(ResourceUtils.CLASSPATH_URL_PREFIX + path);
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
