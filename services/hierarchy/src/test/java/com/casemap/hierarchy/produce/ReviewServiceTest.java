package com.casemap.hierarchy.produce;

import com.casemap.hierarchy.asset.CaseAssetStore;
import com.casemap.hierarchy.asset.CaseQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewServiceTest {

    @TempDir
    Path tempDirectory;

    private ReviewService reviewService;
    private ReviewQueueStore reviewQueueStore;
    private ImportBatchStore importBatchStore;
    private CaseQueryService caseQueryService;
    private ImportBatch batch;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        reviewQueueStore = new ReviewQueueStore(
                objectMapper,
                tempDirectory.resolve("review_queue.json").toString()
        );
        reviewQueueStore.init();
        importBatchStore = new ImportBatchStore(
                objectMapper,
                tempDirectory.resolve("import_batches.json").toString()
        );
        importBatchStore.init();
        CaseAssetStore caseAssetStore = new CaseAssetStore(
                objectMapper,
                tempDirectory.resolve("case_assets.json").toString()
        );
        reviewService = new ReviewService(reviewQueueStore, importBatchStore, caseAssetStore);
        caseQueryService = new CaseQueryService(caseAssetStore);

        batch = new ImportBatch();
        batch.setId("12345678-1234-1234-1234-123456789012");
        batch.setDomain("家装");
        batch.setSystem("报价");
        batch.setMode(ProductionMode.STANDARD);
        batch.setCasesImported(true);
        batch.setKnowledgeImported(true);
        batch.setKeywordsConfirmed(true);
        batch.setKeywordExtraction(new KnowledgeExtractionResult(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "confirmed",
                "knowledge-sha256-version"
        ));
        batch.setMapDraftGenerated(true);
        batch.setReviewQueueGenerated(true);
        batch.setCaseImport(new CaseFileParseResult(
                null,
                List.of(new ParsedCaseRow(
                        "TC-HISTORY-001",
                        "造价单自动审核通过",
                        "提交造价单",
                        "状态变为审核通过",
                        "造价审核",
                        "自动审核",
                        "",
                        2
                )),
                List.of()
        ));
        batch.refreshGate();
        importBatchStore.save(batch);
    }

    @Test
    void reviewsHistoricalAndGapCasesThenPublishesOfficialAssets() {
        MapDraftResult mapDraft = new MapDraftResult(
                batch.getId(),
                List.of(new CleanedCaseItem(
                        "TC-HISTORY-001",
                        "造价单自动审核通过",
                        "造价审核",
                        "自动审核",
                        "",
                        90,
                        "自动挂载",
                        List.of("历史用例已有功能点"),
                        2
                )),
                List.of(new GapCaseCandidate(
                        "GAP-001",
                        "造价审核",
                        "催审",
                        "知识库功能点未匹配历史用例"
                )),
                new MapDraftStats(1, 1, 0, 0, 1),
                "2026-09-07T10:00:00Z"
        );
        reviewService.createQueue(batch, mapDraft);

        ReviewQueueResult pendingQueue = reviewService.query(
                batch.getId(),
                ReviewStatus.PENDING,
                null,
                null
        );
        assertEquals(2, pendingQueue.total());
        assertEquals(1, reviewService.query(
                batch.getId(),
                "报价",
                ReviewStatus.PENDING,
                null,
                "A",
                null
        ).total());
        assertEquals(1, reviewService.query(
                batch.getId(),
                "报价",
                ReviewStatus.PENDING,
                null,
                "B",
                null
        ).total());
        ReviewItem historicalReview = pendingQueue.items().stream()
                .filter(item -> item.getKind() == ReviewItemKind.HISTORICAL_CASE)
                .findFirst()
                .orElseThrow();
        ReviewItem gapReview = pendingQueue.items().stream()
                .filter(item -> item.getKind() == ReviewItemKind.KNOWLEDGE_GAP)
                .findFirst()
                .orElseThrow();

        reviewService.confirm(historicalReview.getId(), null);
        assertThrows(
                IllegalArgumentException.class,
                () -> reviewService.confirm(gapReview.getId(), null)
        );
        UpdateReviewItemRequest gapUpdate = new UpdateReviewItemRequest();
        gapUpdate.setStep("点击催审");
        gapUpdate.setExpected("记录催审时间并置顶");
        gapUpdate.setOperator("评审人");
        reviewService.update(gapReview.getId(), gapUpdate);
        reviewService.confirm(gapReview.getId(), null);

        PublishBatchResult publishResult = reviewService.publishBatch(batch.getId(), null);

        assertEquals(2, publishResult.publishedCount());
        ReviewQueueResult publishedQueue = reviewService.query(
                batch.getId(),
                ReviewStatus.PUBLISHED,
                null,
                null
        );
        assertEquals(2, publishedQueue.total());
        assertTrue(publishedQueue.items().stream().allMatch(item -> item.getOfficialCaseId() != null));
        assertTrue(publishedQueue.items().stream().allMatch(item -> item.getOperations().size() >= 3));

        CaseQueryService.CaseQueryResult autoAuditCases =
                caseQueryService.query(null, "自动审核", "造价审核");
        assertTrue(autoAuditCases.getItems().stream()
                .anyMatch(item -> "造价单自动审核通过".equals(item.getName())));
        assertEquals(
                "TC-HISTORY-001",
                autoAuditCases.getItems().stream()
                        .filter(item -> "造价单自动审核通过".equals(item.getName()))
                        .findFirst()
                        .orElseThrow()
                        .getOriginalCaseId()
        );
        assertEquals(
                "knowledge-sha256-version",
                autoAuditCases.getItems().stream()
                        .filter(item -> "造价单自动审核通过".equals(item.getName()))
                        .findFirst()
                        .orElseThrow()
                        .getKnowledgeSourceVersion()
        );
        CaseQueryService.CaseQueryResult urgentReviewCases =
                caseQueryService.query(null, "催审", "造价审核");
        assertEquals(1, urgentReviewCases.getTotal());
        assertEquals("点击催审", urgentReviewCases.getItems().get(0).getStep());
        assertEquals(2, importBatchStore.get(batch.getId()).orElseThrow().getPublishedCount());
    }

    @Test
    void batchConfirmationReportsPerItemFailures() {
        MapDraftResult mapDraft = new MapDraftResult(
                batch.getId(),
                List.of(new CleanedCaseItem(
                        "TC-HISTORY-001",
                        "造价单自动审核通过",
                        "造价审核",
                        "自动审核",
                        "",
                        90,
                        "自动挂载",
                        List.of(),
                        2
                )),
                List.of(new GapCaseCandidate("GAP-001", "造价审核", "催审", "缺口")),
                new MapDraftStats(1, 1, 0, 0, 1),
                "2026-09-07T10:00:00Z"
        );
        reviewService.createQueue(batch, mapDraft);
        List<String> reviewIds = reviewQueueStore.listAll().stream().map(ReviewItem::getId).toList();
        BatchReviewRequest request = new BatchReviewRequest();
        request.setReviewIds(reviewIds);
        request.setOperator("批量评审人");

        BatchReviewResult result = reviewService.confirmBatch(request);

        assertEquals(2, result.requested());
        assertEquals(1, result.succeeded());
        assertEquals(1, result.failed());
        assertFalse(result.failures().get(0).reason().isBlank());
    }
}
