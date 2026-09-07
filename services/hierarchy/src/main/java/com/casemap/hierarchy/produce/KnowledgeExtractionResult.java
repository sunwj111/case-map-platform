package com.casemap.hierarchy.produce;

import java.util.List;

public record KnowledgeExtractionResult(
        List<KeywordCandidate> scenes,
        List<KeywordCandidate> features,
        List<KeywordCandidate> rules,
        List<KeywordCandidate> nodes,
        String mode,
        String sourceVersion
) {
}
