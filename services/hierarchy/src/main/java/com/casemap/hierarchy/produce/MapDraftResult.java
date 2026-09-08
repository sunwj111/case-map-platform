package com.casemap.hierarchy.produce;

import java.util.List;

public record MapDraftResult(
        String batchId,
        List<CleanedCaseItem> cases,
        List<GapCaseCandidate> gaps,
        MapDraftStats stats,
        String generatedAt
) {
}
