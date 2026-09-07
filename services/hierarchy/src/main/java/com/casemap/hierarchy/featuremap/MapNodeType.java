package com.casemap.hierarchy.featuremap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MapNodeType {
    feature,
    neighbor,
    scene,
    case_node("case"),
    api,
    rule,
    script,
    defect,
    data,
    execution,
    service,
    module;

    private final String wireName;

    MapNodeType() {
        this.wireName = name();
    }

    MapNodeType(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static MapNodeType fromWire(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MapNodeType 不能为空");
        }
        for (MapNodeType type : values()) {
            if (type.wireName.equals(value) || type.name().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知 MapNodeType: " + value);
    }
}
