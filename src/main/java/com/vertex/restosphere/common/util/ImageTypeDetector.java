package com.vertex.restosphere.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

public final class ImageTypeDetector {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private ImageTypeDetector() {
    }

    public static Optional<String> detect(byte[] bytes) {
        if (startsWith(bytes, 0, PNG)) {
            return Optional.of("image/png");
        }
        if (startsWith(bytes, 0, JPEG)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(bytes, 0, RIFF) && startsWith(bytes, 8, WEBP)) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, int offset, byte[] prefix) {
        return bytes.length >= offset + prefix.length
                && Arrays.equals(bytes, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
