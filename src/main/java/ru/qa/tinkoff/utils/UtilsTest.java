package ru.qa.tinkoff.utils;

import com.google.protobuf.ByteString;
import com.sun.istack.Nullable;
import ru.tinkoff.invest.tracking.slave.portfolio.SlavePortfolioOuterClass;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Optional;
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

    private static final char[] HEX_ARRAY = "0123456789ABCDEF" .toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static UUID getGuidFromByteArray(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long high = bb.getLong();
        long low = bb.getLong();
        return new UUID(high, low);
    }

    public byte[] getBytes(com.google.protobuf.GeneratedMessageV3 message) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        message.writeTo(byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }


    public static Optional<BigDecimal> fromUnscaledDecimal(@Nullable SlavePortfolioOuterClass.Decimal decimal) {
        if (decimal == null) {
            return Optional.empty();
        }
        return Optional.of(new BigDecimal(new BigInteger(String.valueOf(decimal.getUnscaled())), decimal.getScale()));
    }

}
