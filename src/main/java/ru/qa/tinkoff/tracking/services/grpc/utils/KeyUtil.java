package ru.qa.tinkoff.tracking.services.grpc.utils;

import io.grpc.Metadata;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

public class KeyUtil {
    public static Metadata.Key<String> createKey(String value) {
        return Metadata.Key.of(value, ASCII_STRING_MARSHALLER);
    }
}
