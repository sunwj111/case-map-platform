package com.casemap.hierarchy.produce;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProductionMode {
    STANDARD("standard"),
    CASES_ONLY("casesOnly");

    private final String value;

    ProductionMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static ProductionMode fromValue(String value) {
        for (ProductionMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("不支持的生产模式：" + value);
    }
}
