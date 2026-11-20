package com.vertex.restosphere.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.service.ImageStorageService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CloudinaryImageStorageServiceImpl implements ImageStorageService {

    private final ObjectProvider<Cloudinary> cloudinary;

    @Override
    public boolean isEnabled() {
        return cloudinary.getIfAvailable() != null;
    }

    @Override
    public String uploadRestaurantImage(String kind, byte[] bytes) {
        Object url;
        try {
            Map<?, ?> result = cloudinary.getObject().uploader().upload(bytes, ObjectUtils.asMap(
                    "folder", "restosphere/restaurant", "public_id", kind,
                    "overwrite", true, "invalidate", true, "resource_type", "image"));
            url = result == null ? null : result.get("secure_url");
        } catch (Exception e) {
            throw uploadFailed();
        }
        if (!(url instanceof String s) || s.isBlank()) {
            throw uploadFailed();
        }
        return s;
    }

    private static ApiException uploadFailed() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "Could not upload image");
    }
}
