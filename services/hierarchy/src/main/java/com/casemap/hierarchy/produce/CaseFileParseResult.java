package com.casemap.hierarchy.produce;

import java.util.List;

public record CaseFileParseResult(
        ImportFileMetadata metadata,
        List<ParsedCaseRow> rows,
        List<ImportRisk> risks
) {
}
