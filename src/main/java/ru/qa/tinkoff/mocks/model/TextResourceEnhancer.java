package ru.qa.tinkoff.mocks.model;

import lombok.SneakyThrows;

import java.io.IOException;
import java.io.InputStream;
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
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        //File file = ResourceUtils.getFile(ResourceUtils.CLASSPATH_URL_PREFIX + path);
        try (InputStream inputStream = classLoader.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new RuntimeException("Resource not found: " + path);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
