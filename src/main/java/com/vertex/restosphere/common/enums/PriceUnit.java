package com.vertex.restosphere.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.EnumeratedValue;

public enum PriceUnit {
    PORTION("portion"),
    KG("kg"),
    GLASS("glass"),
    BOTTLE("bottle");

    @EnumeratedValue @JsonValue private final String value;

    PriceUnit(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
