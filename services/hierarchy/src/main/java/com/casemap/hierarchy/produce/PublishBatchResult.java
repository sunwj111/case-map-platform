package com.casemap.hierarchy.produce;

import java.util.List;

public record PublishBatchResult(
        String batchId,
        int publishedCount,
        List<String> officialCaseIds,
        String publishedAt
) {
}
