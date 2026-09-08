package com.casemap.hierarchy.produce;

import com.casemap.hierarchy.asset.CaseAsset;
import com.casemap.hierarchy.asset.CaseAssetStore;
import com.casemap.hierarchy.featurekey.FeatureKey;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReviewService {

    private final ReviewQueueStore reviewQueueStore;
    private final ImportBatchStore importBatchStore;
    private final CaseAssetStore caseAssetStore;

    public ReviewService(
            ReviewQueueStore reviewQueueStore,
            ImportBatchStore importBatchStore,
            CaseAssetStore caseAssetStore
    ) {
        this.reviewQueueStore = reviewQueueStore;
        this.importBatchStore = importBatchStore;
        this.caseAssetStore = caseAssetStore;
    }

    public void createQueue(ImportBatch batch, MapDraftResult mapDraft) {
        String currentTime = Instant.now().toString();
        Map<String, ParsedCaseRow> sourceRows = new LinkedHashMap<>();
        for (ParsedCaseRow sourceRow : batch.getCaseImport().rows()) {
            sourceRows.put(sourceRow.originalCaseId(), sourceRow);
        }
        List<ReviewItem> reviewItems = new ArrayList<>();
        int historicalIndex = 1;
        for (CleanedCaseItem cleanedCase : mapDraft.cases()) {
            ParsedCaseRow sourceRow = sourceRows.get(cleanedCase.originalCaseId());
            ReviewItem reviewItem = baseReviewItem(
                    reviewId(batch.getId(), "H", historicalIndex++),
                    batch.getId(),
                    ReviewItemKind.HISTORICAL_CASE,
                    currentTime
            );
            reviewItem.setOriginalCaseId(cleanedCase.originalCaseId());
            reviewItem.setCaseName(cleanedCase.caseName());
            reviewItem.setScene(cleanedCase.scene());
            reviewItem.setFeature(cleanedCase.feature());
            reviewItem.setFlowNode(cleanedCase.flowNode());
            reviewItem.setStep(sourceRow == null ? "" : sourceRow.step());
            reviewItem.setExpected(sourceRow == null ? "" : sourceRow.expected());
            reviewItem.setPriority(cleanedCase.confidence() >= 85 ? "P0" : "P1");
            reviewItem.setConfidence(cleanedCase.confidence());
            reviewItem.setMatchEvidence(cleanedCase.matchEvidence());
            reviewItems.add(reviewItem);
        }
        int gapIndex = 1;
        for (GapCaseCandidate gap : mapDraft.gaps()) {
            ReviewItem reviewItem = baseReviewItem(
                    reviewId(batch.getId(), "G", gapIndex++),
                    batch.getId(),
                    ReviewItemKind.KNOWLEDGE_GAP,
                    currentTime
            );
            reviewItem.setOriginalCaseId(gap.id());
            reviewItem.setCaseName("缺口用例：" + gap.feature());
            reviewItem.setScene(gap.scene());
            reviewItem.setFeature(gap.feature());
            reviewItem.setFlowNode("");
            reviewItem.setStep("");
            reviewItem.setExpected("");
            reviewItem.setPriority("P1");
            reviewItem.setConfidence(60);
            reviewItem.setMatchEvidence(List.of(gap.reason()));
            reviewItems.add(reviewItem);
        }
        reviewQueueStore.replaceBatchDraft(batch.getId(), reviewItems);
    }

    public ReviewQueueResult query(
            String batchId,
            ReviewStatus status,
            ReviewItemKind kind,
            String keyword
    ) {
        return query(batchId, null, status, kind, null, keyword);
    }

    public ReviewQueueResult query(
            String batchId,
            String system,
            ReviewStatus status,
            ReviewItemKind kind,
            String pool,
            String keyword
    ) {
        String normalizedKeyword = normalize(keyword);
        String normalizedSystem = system == null ? "" : system.trim();
        String normalizedPool = pool == null ? "" : pool.trim().toUpperCase(Locale.ROOT);
        if (!normalizedPool.isEmpty() && !List.of("A", "B", "C").contains(normalizedPool)) {
            throw new IllegalArgumentException("匹配池仅支持 A、B、C");
        }
        List<ReviewItem> matchedItems = reviewQueueStore.listAll().stream()
                .filter(item -> batchId == null || batchId.isBlank() || batchId.equals(item.getBatchId()))
                .filter(item -> normalizedSystem.isEmpty() || matchesSystem(item, normalizedSystem))
                .filter(item -> status == null || status == item.getStatus())
                .filter(item -> kind == null || kind == item.getKind())
                .filter(item -> normalizedPool.isEmpty() || normalizedPool.equals(poolOf(item.getConfidence())))
                .filter(item -> normalizedKeyword.isEmpty() || searchableText(item).contains(normalizedKeyword))
                .toList();
        int pending = countStatus(matchedItems, ReviewStatus.PENDING);
        int confirmed = countStatus(matchedItems, ReviewStatus.CONFIRMED);
        int discarded = countStatus(matchedItems, ReviewStatus.DISCARDED);
        int published = countStatus(matchedItems, ReviewStatus.PUBLISHED);
        return new ReviewQueueResult(
                matchedItems.size(),
                pending,
                confirmed,
                discarded,
                published,
                matchedItems
        );
    }

    private boolean matchesSystem(ReviewItem item, String system) {
        return importBatchStore.get(item.getBatchId())
                .map(ImportBatch::getSystem)
                .map(system::equals)
                .orElse(false);
    }

    private static String poolOf(int confidence) {
        if (confidence >= 80) {
            return "A";
        }
        if (confidence >= 60) {
            return "B";
        }
        return "C";
    }

    public ReviewItem update(String reviewId, UpdateReviewItemRequest request) {
        return reviewQueueStore.update(reviewId, item -> {
            ensurePending(item);
            applyIfProvided(request.getCaseName(), item::setCaseName);
            applyIfProvided(request.getScene(), item::setScene);
            applyIfProvided(request.getFeature(), item::setFeature);
            applyIfProvided(request.getFlowNode(), item::setFlowNode);
            applyIfProvided(request.getStep(), item::setStep);
            applyIfProvided(request.getExpected(), item::setExpected);
            applyIfProvided(request.getPriority(), item::setPriority);
            addOperation(item, "调整", operator(request.getOperator()), "调整用例内容或挂载");
        });
    }

    public ReviewItem confirm(String reviewId, ReviewActionRequest request) {
        return reviewQueueStore.update(reviewId, item -> {
            ensurePending(item);
            validateForConfirmation(item);
            item.setStatus(ReviewStatus.CONFIRMED);
            addOperation(item, "确认", operator(request == null ? null : request.getOperator()),
                    request == null ? "" : request.getComment());
        });
    }

    public ReviewItem discard(String reviewId, ReviewActionRequest request) {
        return reviewQueueStore.update(reviewId, item -> {
            if (item.getStatus() == ReviewStatus.PUBLISHED) {
                throw new IllegalArgumentException("已发布用例不能在评审队列中废弃");
            }
            if (item.getStatus() == ReviewStatus.DISCARDED) {
                throw new IllegalArgumentException("评审项已废弃");
            }
            item.setStatus(ReviewStatus.DISCARDED);
            addOperation(item, "废弃", operator(request == null ? null : request.getOperator()),
                    request == null ? "" : request.getComment());
        });
    }

    public BatchReviewResult confirmBatch(BatchReviewRequest request) {
        List<String> requestedIds = request.getReviewIds();
        if (requestedIds.isEmpty()) {
            throw new IllegalArgumentException("请选择需要确认的评审项");
        }
        List<String> succeededIds = new ArrayList<>();
        List<BatchReviewResult.BatchReviewFailure> failures = new ArrayList<>();
        for (String reviewId : requestedIds) {
            try {
                ReviewItem reviewItem = reviewQueueStore.get(reviewId)
                        .orElseThrow(() -> new IllegalArgumentException("评审项不存在：" + reviewId));
                if (reviewItem.getConfidence() < 80) {
                    throw new IllegalArgumentException("仅 A 池（置信度不低于 80）支持批量确认");
                }
                ReviewActionRequest actionRequest = new ReviewActionRequest();
                actionRequest.setOperator(request.getOperator());
                confirm(reviewId, actionRequest);
                succeededIds.add(reviewId);
            } catch (IllegalArgumentException exception) {
                failures.add(new BatchReviewResult.BatchReviewFailure(reviewId, exception.getMessage()));
            }
        }
        return new BatchReviewResult(
                requestedIds.size(),
                succeededIds.size(),
                failures.size(),
                List.copyOf(succeededIds),
                List.copyOf(failures)
        );
    }

    public PublishBatchResult publishBatch(String batchId, ReviewActionRequest request) {
        ImportBatch batch = importBatchStore.get(batchId)
                .orElseThrow(() -> new IllegalArgumentException("导入批次不存在：" + batchId));
        List<ReviewItem> confirmedItems = reviewQueueStore.listAll().stream()
                .filter(item -> batchId.equals(item.getBatchId()))
                .filter(item -> item.getStatus() == ReviewStatus.CONFIRMED)
                .toList();
        if (confirmedItems.isEmpty()) {
            throw new IllegalArgumentException("当前批次没有已确认且待发布的用例");
        }
        String publishedAt = Instant.now().toString();
        List<CaseAsset> assets = confirmedItems.stream()
                .map(item -> toOfficialAsset(batch, item, publishedAt))
                .toList();
        caseAssetStore.publish(assets);

        String operator = operator(request == null ? null : request.getOperator());
        List<String> reviewIds = confirmedItems.stream().map(ReviewItem::getId).toList();
        reviewQueueStore.updateMany(reviewIds, item -> {
            item.setStatus(ReviewStatus.PUBLISHED);
            item.setOfficialCaseId(officialCaseId(item.getId()));
            addOperation(item, "发布", operator, request == null ? "" : request.getComment());
        });
        importBatchStore.update(batchId, currentBatch ->
                currentBatch.setPublishedCount(currentBatch.getPublishedCount() + assets.size()));
        return new PublishBatchResult(
                batchId,
                assets.size(),
                assets.stream().map(CaseAsset::getId).toList(),
                publishedAt
        );
    }

    private static ReviewItem baseReviewItem(
            String reviewId,
            String batchId,
            ReviewItemKind kind,
            String currentTime
    ) {
        ReviewItem reviewItem = new ReviewItem();
        reviewItem.setId(reviewId);
        reviewItem.setBatchId(batchId);
        reviewItem.setKind(kind);
        reviewItem.setStatus(ReviewStatus.PENDING);
        reviewItem.setCreatedAt(currentTime);
        reviewItem.setUpdatedAt(currentTime);
        reviewItem.setOperations(List.of(new ReviewOperation(
                "生成",
                "系统",
                kind == ReviewItemKind.HISTORICAL_CASE ? "历史用例清洗进入评审" : "知识缺口进入评审",
                currentTime
        )));
        return reviewItem;
    }

    private static CaseAsset toOfficialAsset(
            ImportBatch batch,
            ReviewItem reviewItem,
            String publishedAt
    ) {
        CaseAsset asset = new CaseAsset();
        asset.setId(officialCaseId(reviewItem.getId()));
        asset.setName(reviewItem.getCaseName());
        asset.setPriority(reviewItem.getPriority());
        asset.setTestScenario(reviewItem.getScene());
        asset.setFeatureName(reviewItem.getFeature());
        asset.setSceneName(reviewItem.getScene());
        asset.setFeatureKey(FeatureKey.join(
                batch.getDomain(),
                batch.getSystem(),
                reviewItem.getScene(),
                reviewItem.getFeature()
        ));
        asset.setStatus(CaseAsset.STATUS_CONFIRMED);
        asset.setLifecycle("已发布");
        asset.setApi("");
        asset.setConfidence(reviewItem.getConfidence());
        asset.setStep(reviewItem.getStep());
        asset.setExpected(reviewItem.getExpected());
        asset.setSourceBatchId(reviewItem.getBatchId());
        asset.setSourceReviewId(reviewItem.getId());
        asset.setOriginalCaseId(reviewItem.getOriginalCaseId());
        asset.setKnowledgeSourceVersion(
                batch.getKeywordExtraction() == null ? "" : batch.getKeywordExtraction().sourceVersion()
        );
        asset.setSourceType(reviewItem.getKind().value());
        asset.setFlowNode(reviewItem.getFlowNode());
        asset.setCreatedAt(publishedAt);
        asset.setUpdatedAt(publishedAt);
        return asset;
    }

    private static void validateForConfirmation(ReviewItem item) {
        requireText(item.getCaseName(), "用例名称");
        requireText(item.getScene(), "业务场景");
        requireText(item.getFeature(), "功能点");
        requireFeatureKeySegment(item.getScene(), "业务场景");
        requireFeatureKeySegment(item.getFeature(), "功能点");
        if (!List.of("P0", "P1", "P2").contains(item.getPriority())) {
            throw new IllegalArgumentException("优先级仅支持 P0、P1、P2");
        }
        if (item.getKind() == ReviewItemKind.KNOWLEDGE_GAP) {
            requireText(item.getStep(), "缺口用例步骤");
            requireText(item.getExpected(), "缺口用例预期");
        }
    }

    private static void ensurePending(ReviewItem item) {
        if (item.getStatus() != ReviewStatus.PENDING) {
            throw new IllegalArgumentException("仅待评审状态允许此操作，当前状态：" + item.getStatus().value());
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
    }

    private static void requireFeatureKeySegment(String value, String fieldName) {
        if (value.contains("/") || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException(fieldName + "不能包含斜杠或换行");
        }
    }

    private static void applyIfProvided(String value, java.util.function.Consumer<String> updater) {
        if (value != null) {
            updater.accept(value.trim());
        }
    }

    private static void addOperation(
            ReviewItem item,
            String action,
            String operator,
            String detail
    ) {
        List<ReviewOperation> operations = new ArrayList<>(item.getOperations());
        operations.add(new ReviewOperation(
                action,
                operator,
                detail == null ? "" : detail.trim(),
                Instant.now().toString()
        ));
        item.setOperations(operations);
    }

    private static int countStatus(List<ReviewItem> items, ReviewStatus status) {
        return (int) items.stream().filter(item -> item.getStatus() == status).count();
    }

    private static String searchableText(ReviewItem item) {
        return normalize(String.join(
                " ",
                item.getCaseName(),
                item.getScene(),
                item.getFeature(),
                item.getFlowNode(),
                item.getOriginalCaseId()
        ));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String operator(String operator) {
        return operator == null || operator.isBlank() ? "当前用户" : operator.trim();
    }

    private static String reviewId(String batchId, String type, int index) {
        return "RV-" + batchId.substring(0, Math.min(8, batchId.length()))
                + "-" + type + "-" + String.format("%03d", index);
    }

    private static String officialCaseId(String reviewId) {
        return "OFF-" + reviewId.substring(3);
    }
}
