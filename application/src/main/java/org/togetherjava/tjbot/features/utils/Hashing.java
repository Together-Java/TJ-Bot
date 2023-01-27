package org.togetherjava.tjbot.features.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Utility for hashing data.
 */
public class Hashing {
    private Hashing() {
        throw new UnsupportedOperationException("Utility class, construction not supported");
    }

    /**
     * All characters available in the hexadecimal-system, as UTF-8 encoded array.
     */
    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.UTF_8);

    /**
     * Creates a hexadecimal representation of the given binary data.
     *
     * @param bytes the binary data to convert
     * @return a hexadecimal representation
     */
    public static String bytesToHex(byte[] bytes) {
        Objects.requireNonNull(bytes);
        // See https://stackoverflow.com/a/9855338/2411243
        // noinspection MultiplyOrDivideByPowerOfTwo
        final byte[] hexChars = new byte[bytes.length * 2];
        // noinspection ArrayLengthInLoopCondition
        for (int j = 0; j < bytes.length; j++) {
            final int v = bytes[j] & 0xFF;
            // noinspection MultiplyOrDivideByPowerOfTwo
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            // noinspection MultiplyOrDivideByPowerOfTwo
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }

    /**
     * Hashes the given data using the given method.
     *
     * @param method the method to use for hashing, must be supported by {@link MessageDigest}, e.g.
     *        {@code "SHA"}
     * @param data the data to hash
     * @return the computed hash
     */
    public static byte[] hash(String method, byte[] data) {
        Objects.requireNonNull(method);
        Objects.requireNonNull(data);
        try {
            return MessageDigest.getInstance(method).digest(data);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("Hash method must be supported", e);
        }
    }
}
