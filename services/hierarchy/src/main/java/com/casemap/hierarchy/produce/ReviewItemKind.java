package com.casemap.hierarchy.produce;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ReviewItemKind {
    HISTORICAL_CASE("历史用例"),
    KNOWLEDGE_GAP("知识缺口");

    private final String value;

    ReviewItemKind(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static ReviewItemKind fromValue(String value) {
        for (ReviewItemKind kind : values()) {
            if (kind.value.equals(value) || kind.name().equalsIgnoreCase(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("不支持的评审类型：" + value);
    }
}
