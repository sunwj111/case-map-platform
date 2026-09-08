package com.casemap.hierarchy.web;

import com.casemap.hierarchy.produce.BatchReviewRequest;
import com.casemap.hierarchy.produce.BatchReviewResult;
import com.casemap.hierarchy.produce.PublishBatchResult;
import com.casemap.hierarchy.produce.ReviewActionRequest;
import com.casemap.hierarchy.produce.ReviewItem;
import com.casemap.hierarchy.produce.ReviewItemKind;
import com.casemap.hierarchy.produce.ReviewQueueResult;
import com.casemap.hierarchy.produce.ReviewService;
import com.casemap.hierarchy.produce.ReviewStatus;
import com.casemap.hierarchy.produce.UpdateReviewItemRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ReviewQueueResult query(
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) String system,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String pool,
            @RequestParam(required = false) String keyword
    ) {
        return reviewService.query(
                batchId,
                system,
                parseStatus(status),
                parseKind(kind),
                pool,
                keyword
        );
    }

    @PatchMapping("/{reviewId}")
    public ReviewItem update(
            @PathVariable String reviewId,
            @RequestBody UpdateReviewItemRequest request
    ) {
        return reviewService.update(reviewId, request);
    }

    @PostMapping("/{reviewId}/confirm")
    public ReviewItem confirm(
            @PathVariable String reviewId,
            @RequestBody(required = false) ReviewActionRequest request
    ) {
        return reviewService.confirm(reviewId, request);
    }

    @PostMapping("/{reviewId}/discard")
    public ReviewItem discard(
            @PathVariable String reviewId,
            @RequestBody(required = false) ReviewActionRequest request
    ) {
        return reviewService.discard(reviewId, request);
    }

    @PostMapping("/batch/confirm")
    public BatchReviewResult confirmBatch(@RequestBody BatchReviewRequest request) {
        return reviewService.confirmBatch(request);
    }

    @PostMapping("/batches/{batchId}/publish")
    public PublishBatchResult publishBatch(
            @PathVariable String batchId,
            @RequestBody(required = false) ReviewActionRequest request
    ) {
        return reviewService.publishBatch(batchId, request);
    }

    private static ReviewStatus parseStatus(String value) {
        return value == null || value.isBlank() ? null : ReviewStatus.fromValue(value);
    }

    private static ReviewItemKind parseKind(String value) {
        return value == null || value.isBlank() ? null : ReviewItemKind.fromValue(value);
    }
}
