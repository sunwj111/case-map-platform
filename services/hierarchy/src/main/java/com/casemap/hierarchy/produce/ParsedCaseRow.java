package com.casemap.hierarchy.produce;

public record ParsedCaseRow(
        String originalCaseId,
        String caseName,
        String step,
        String expected,
        String scene,
        String feature,
        String module,
        int sourceRowNumber
) {
}
