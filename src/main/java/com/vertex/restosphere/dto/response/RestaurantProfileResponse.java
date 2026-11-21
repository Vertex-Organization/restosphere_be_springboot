package com.vertex.restosphere.dto.response;

import com.vertex.restosphere.entity.RestaurantSettings;
import java.util.Objects;

public record RestaurantProfileResponse(
        String name,
        String description,
        String address,
        String hotline,
        String email,
        String openingHours,
        String facebook,
        String instagram,
        String tiktok,
        String logoUrl,
        String coverUrl) {

    public static RestaurantProfileResponse from(RestaurantSettings s) {
        return new RestaurantProfileResponse(
                str(s.getName()),
                str(s.getDescription()),
                str(s.getAddress()),
                str(s.getPhone()),
                str(s.getEmail()),
                str(s.getOpeningHours()),
                str(s.getFacebook()),
                str(s.getInstagram()),
                str(s.getTiktok()),
                str(s.getLogoUrl()),
                str(s.getCoverUrl()));
    }

    private static String str(String value) {
        return Objects.toString(value, "");
    }
}
