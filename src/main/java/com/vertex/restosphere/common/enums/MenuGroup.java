package com.vertex.restosphere.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.EnumeratedValue;

public enum MenuGroup {
    FOOD("food"),
    DRINK("drink");

    @EnumeratedValue @JsonValue private final String value;

    MenuGroup(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
