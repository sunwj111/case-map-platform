package com.casemap.hierarchy.produce;

public record MapDraftStats(
        int totalCases,
        int autoMounted,
        int reviewRequired,
        int manualRequired,
        int gapCount
) {
}
