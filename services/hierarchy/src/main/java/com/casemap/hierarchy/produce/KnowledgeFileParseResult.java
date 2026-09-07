package com.casemap.hierarchy.produce;

import java.util.List;

public record KnowledgeFileParseResult(
        ImportFileMetadata metadata,
        String text,
        int lineCount,
        List<ImportRisk> risks
) {
}
