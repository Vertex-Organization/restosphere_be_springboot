package com.vertex.restosphere.common.util;

import com.vertex.restosphere.exception.ApiException;
import java.util.Base64;
import org.springframework.http.HttpStatus;

public final class Base64ImageDecoder {

    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private Base64ImageDecoder() {}

    public static byte[] decode(String contentType, String data) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw invalid("Invalid image data");
        }
        if (bytes.length == 0) {
            throw invalid("Invalid image data");
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw invalid("Image must be at most 5 MB");
        }
        if (ImageTypeDetector.detect(bytes).filter(contentType::equals).isEmpty()) {
            throw invalid("Only PNG, JPEG or WebP images are allowed");
        }
        return bytes;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
