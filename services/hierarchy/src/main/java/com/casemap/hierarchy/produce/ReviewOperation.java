package com.casemap.hierarchy.produce;

public record ReviewOperation(
        String action,
        String operator,
        String detail,
        String operatedAt
) {
}
