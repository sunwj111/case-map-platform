package com.casemap.hierarchy.produce;

import java.util.List;

public record CleanedCaseItem(
        String originalCaseId,
        String caseName,
        String scene,
        String feature,
        String flowNode,
        int confidence,
        String mountAdvice,
        List<String> matchEvidence,
        int sourceRowNumber
) {
}
