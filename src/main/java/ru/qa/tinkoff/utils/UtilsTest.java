package ru.qa.tinkoff.utils;

import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

public class UtilsTest {

    public ByteString buildByteString(UUID uuid) {
        if (uuid == null) {
            return ByteString.EMPTY;
        }
        ByteBuffer uuidByteBuffer = ByteBuffer.allocate(16);
        uuidByteBuffer.putLong(uuid.getMostSignificantBits());
        uuidByteBuffer.putLong(uuid.getLeastSignificantBits());
        uuidByteBuffer.flip();
        return ByteString.copyFrom(uuidByteBuffer);
    }

    public byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public byte[] getBytes(com.google.protobuf.GeneratedMessageV3 message) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        message.writeTo(byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }

}
