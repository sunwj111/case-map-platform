package com.casemap.hierarchy.produce;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ReviewStatus {
    PENDING("待评审"),
    CONFIRMED("已确认"),
    DISCARDED("已废弃"),
    PUBLISHED("已发布");

    private final String value;

    ReviewStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static ReviewStatus fromValue(String value) {
        for (ReviewStatus status : values()) {
            if (status.value.equals(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("不支持的评审状态：" + value);
    }
}
