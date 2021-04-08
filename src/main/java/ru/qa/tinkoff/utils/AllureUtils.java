package ru.qa.tinkoff.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import lombok.SneakyThrows;

import java.util.ArrayList;

public class AllureUtils {
    public static final String FOUND_ENTITIES = "Найденные записи";
    public static final String ADDED_ENTITIES = "Добавленные записи";
    public static final String DELETED_ENTITIES = "Удалённые записи";
    public static final String EXPECTED_RESULT = "Ожидаемый результат";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private AllureUtils() {
    }

    public static void cleanParameters() {
        Allure.getLifecycle().updateTestCase(testResult -> testResult.setParameters(new ArrayList<>()));
    }

    @SneakyThrows
    public static void addJsonAttachment(String msg, Object entity) {
        Allure.addAttachment(msg, "application/json", objectMapper.writeValueAsString(entity));
    }

    @SneakyThrows
    public static void addTextAttachment(String msg, Object entity) {
        Allure.addAttachment(msg, "text/plain", entity.toString());
    }
}