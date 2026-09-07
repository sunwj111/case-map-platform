package com.casemap.hierarchy.produce;

public record KeywordCandidate(
        String text,
        String source,
        int confidence
) {
}
