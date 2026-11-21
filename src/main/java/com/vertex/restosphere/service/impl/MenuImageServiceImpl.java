package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.common.util.Base64ImageDecoder;
import com.vertex.restosphere.dto.request.MenuImageRequest;
import com.vertex.restosphere.dto.response.MenuImageResponse;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.ImageStorageService;
import com.vertex.restosphere.service.MenuImageService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuImageServiceImpl implements MenuImageService {

    private final CurrentUserService currentUser;
    private final ImageStorageService imageStorage;

    @Override
    @Transactional(readOnly = true)
    public MenuImageResponse upload(UUID actorId, MenuImageRequest req) {
        currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
        if (!imageStorage.isEnabled()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Image upload is not configured");
        }
        byte[] bytes = Base64ImageDecoder.decode(req.contentType(), req.data());
        return new MenuImageResponse(imageStorage.uploadMenuImage(bytes));
    }
}
