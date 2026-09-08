package com.casemap.hierarchy.produce;

import java.util.List;

public record BatchReviewResult(
        int requested,
        int succeeded,
        int failed,
        List<String> succeededIds,
        List<BatchReviewFailure> failures
) {
    public record BatchReviewFailure(String reviewId, String reason) {
    }
}
