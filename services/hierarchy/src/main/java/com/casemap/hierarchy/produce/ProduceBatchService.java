package com.casemap.hierarchy.produce;

import com.casemap.hierarchy.model.CascadeOptions;
import com.casemap.hierarchy.store.HierarchyStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProduceBatchService {

    private final HierarchyStore hierarchyStore;
    private final ImportBatchStore importBatchStore;
    private final CaseFileParser caseFileParser;
    private final KnowledgeFileParser knowledgeFileParser;
    private final NaturalLanguageKnowledgeExtractor knowledgeExtractor;
    private final CaseCleaningService caseCleaningService;
    private final ReviewService reviewService;

    public ProduceBatchService(
            HierarchyStore hierarchyStore,
            ImportBatchStore importBatchStore,
            CaseFileParser caseFileParser,
            KnowledgeFileParser knowledgeFileParser,
            NaturalLanguageKnowledgeExtractor knowledgeExtractor,
            CaseCleaningService caseCleaningService,
            ReviewService reviewService
    ) {
        this.hierarchyStore = hierarchyStore;
        this.importBatchStore = importBatchStore;
        this.caseFileParser = caseFileParser;
        this.knowledgeFileParser = knowledgeFileParser;
        this.knowledgeExtractor = knowledgeExtractor;
        this.caseCleaningService = caseCleaningService;
        this.reviewService = reviewService;
    }

    public ImportBatch create(CreateImportBatchRequest request) {
        if (request.getMode() != ProductionMode.STANDARD) {
            throw new IllegalArgumentException("当前仅开放标准模式");
        }

        String domain = normalize(request.getDomain(), "领域");
        String system = normalize(request.getSystem(), "系统");
        validateTarget(domain, system);

        String currentTime = Instant.now().toString();
        ImportBatch batch = new ImportBatch();
        batch.setId(UUID.randomUUID().toString());
        batch.setDomain(domain);
        batch.setSystem(system);
        batch.setMode(ProductionMode.STANDARD);
        batch.setCreatedAt(currentTime);
        batch.setUpdatedAt(currentTime);
        batch.refreshGate();
        return importBatchStore.save(batch);
    }

    public ImportBatch get(String batchId) {
        return find(batchId)
                .orElseThrow(() -> new IllegalArgumentException("导入批次不存在：" + batchId));
    }

    public Optional<ImportBatch> find(String batchId) {
        return importBatchStore.get(batchId);
    }

    public CaseFileParseResult importCases(String batchId, String fileName, byte[] content) {
        ImportBatch batch = get(batchId);
        ensureBatchEditable(batch);
        ensureStandardMode(batch);
        CaseFileParseResult parseResult = caseFileParser.parse(fileName, content);
        importBatchStore.update(batchId, currentBatch -> {
            currentBatch.setCaseImport(parseResult);
            currentBatch.setCasesImported(true);
            currentBatch.setKeywordsConfirmed(false);
            currentBatch.setMapDraftGenerated(false);
            currentBatch.setMapDraft(null);
        });
        return parseResult;
    }

    public KnowledgeFileParseResult importKnowledge(String batchId, String fileName, byte[] content) {
        ImportBatch batch = get(batchId);
        ensureBatchEditable(batch);
        ensureStandardMode(batch);
        KnowledgeFileParseResult parseResult = knowledgeFileParser.parse(fileName, content);
        importBatchStore.update(batchId, currentBatch -> {
            currentBatch.setKnowledgeImport(parseResult);
            currentBatch.setKnowledgeImported(true);
            currentBatch.setKeywordExtraction(null);
            currentBatch.setKeywordsConfirmed(false);
            currentBatch.setMapDraftGenerated(false);
            currentBatch.setMapDraft(null);
        });
        return parseResult;
    }

    public KnowledgeExtractionResult extractKnowledge(String batchId) {
        ImportBatch batch = get(batchId);
        ensureBatchEditable(batch);
        ensureStandardMode(batch);
        if (batch.getKnowledgeImport() == null || batch.getKnowledgeImport().text().isBlank()) {
            throw new IllegalArgumentException("请先上传或粘贴知识库内容");
        }
        String sourceVersion = batch.getKnowledgeImport().metadata().sha256();
        KnowledgeExtractionResult extractionResult = knowledgeExtractor.extract(
                batch.getKnowledgeImport().text(),
                sourceVersion
        );
        importBatchStore.update(batchId, currentBatch -> {
            currentBatch.setKeywordExtraction(extractionResult);
            currentBatch.setKeywordsConfirmed(false);
            currentBatch.setMapDraftGenerated(false);
            currentBatch.setMapDraft(null);
        });
        return extractionResult;
    }

    public KnowledgeExtractionResult importKnowledgeText(
            String batchId,
            String sourceName,
            String text
    ) {
        ImportBatch batch = get(batchId);
        ensureBatchEditable(batch);
        ensureStandardMode(batch);
        String normalizedText = text == null ? "" : text.trim();
        if (normalizedText.isEmpty()) {
            throw new IllegalArgumentException("知识文本不能为空");
        }
        String existingKnowledge = batch.getKnowledgeImport() == null
                ? ""
                : batch.getKnowledgeImport().text().trim();
        String combinedText = existingKnowledge.isEmpty()
                ? normalizedText
                : existingKnowledge + "\n\n# 补充自然语言知识\n\n" + normalizedText;
        byte[] content = combinedText.getBytes(StandardCharsets.UTF_8);
        String normalizedSourceName = sourceName == null || sourceName.isBlank()
                ? "pasted-knowledge.md"
                : sourceName.trim();
        String combinedSourceName = existingKnowledge.isEmpty()
                ? normalizedSourceName
                : batch.getKnowledgeImport().metadata().fileName() + " + " + normalizedSourceName;
        ImportFileMetadata metadata = new ImportFileMetadata(
                combinedSourceName,
                existingKnowledge.isEmpty() ? "pasted-text" : "combined-knowledge",
                content.length,
                FileParsingSupport.sha256(content),
                null,
                null,
                null
        );
        KnowledgeFileParseResult parseResult = new KnowledgeFileParseResult(
                metadata,
                combinedText,
                combinedText.split("\\R", -1).length,
                List.of()
        );
        KnowledgeExtractionResult extractionResult = knowledgeExtractor.extract(
                combinedText,
                metadata.sha256()
        );
        importBatchStore.update(batchId, currentBatch -> {
            currentBatch.setKnowledgeImport(parseResult);
            currentBatch.setKnowledgeImported(true);
            currentBatch.setKeywordExtraction(extractionResult);
            currentBatch.setKeywordsConfirmed(false);
            currentBatch.setMapDraftGenerated(false);
            currentBatch.setMapDraft(null);
        });
        return extractionResult;
    }

    public ImportBatch confirmKeywords(String batchId) {
        return confirmKeywords(batchId, null);
    }

    public ImportBatch confirmKeywords(String batchId, ConfirmKeywordsRequest request) {
        ensureBatchEditable(get(batchId));
        return importBatchStore.update(batchId, batch -> {
            batch.refreshGate();
            if (!batch.isReadyForKeywordCalibration()) {
                throw new IllegalArgumentException("标准模式需先完成历史用例和知识库导入");
            }
            if (batch.getKeywordExtraction() == null) {
                throw new IllegalArgumentException("请先抽取知识库关键字");
            }
            if (request != null && hasConfirmedValues(request)) {
                KnowledgeExtractionResult currentExtraction = batch.getKeywordExtraction();
                batch.setKeywordExtraction(new KnowledgeExtractionResult(
                        confirmedCandidates(request.getScenes(), currentExtraction.scenes()),
                        confirmedCandidates(request.getFeatures(), currentExtraction.features()),
                        confirmedCandidates(request.getRules(), currentExtraction.rules()),
                        confirmedCandidates(request.getNodes(), currentExtraction.nodes()),
                        "confirmed",
                        currentExtraction.sourceVersion()
                ));
            }
            batch.setKeywordsConfirmed(true);
        });
    }

    public MapDraftResult generateMapDraft(String batchId) {
        ImportBatch batch = get(batchId);
        ensureBatchEditable(batch);
        MapDraftResult mapDraft = caseCleaningService.generate(batch);
        reviewService.createQueue(batch, mapDraft);
        importBatchStore.update(batchId, currentBatch -> {
            currentBatch.setMapDraft(mapDraft);
            currentBatch.setMapDraftGenerated(true);
            currentBatch.setReviewQueueGenerated(true);
        });
        return mapDraft;
    }

    private void validateTarget(String domain, String system) {
        CascadeOptions domainOptions = hierarchyStore.cascadeOptions(null, null, null);
        if (!domainOptions.getDomains().contains(domain)) {
            throw new IllegalArgumentException("领域不存在：" + domain);
        }
        CascadeOptions systemOptions = hierarchyStore.cascadeOptions(domain, null, null);
        if (!systemOptions.getSystems().contains(system)) {
            throw new IllegalArgumentException("领域「" + domain + "」下不存在系统「" + system + "」");
        }
    }

    private static void ensureStandardMode(ImportBatch batch) {
        if (batch.getMode() != ProductionMode.STANDARD) {
            throw new IllegalArgumentException("该批次不是标准模式");
        }
    }

    private static void ensureBatchEditable(ImportBatch batch) {
        if (batch.isReviewQueueGenerated()) {
            throw new IllegalArgumentException("当前批次已进入评审，不能重新导入或生成草稿");
        }
    }

    private static boolean hasConfirmedValues(ConfirmKeywordsRequest request) {
        return !request.getScenes().isEmpty()
                || !request.getFeatures().isEmpty()
                || !request.getRules().isEmpty()
                || !request.getNodes().isEmpty();
    }

    private static List<KeywordCandidate> confirmedCandidates(
            List<String> values,
            List<KeywordCandidate> extractedCandidates
    ) {
        LinkedHashSet<String> uniqueValues = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                uniqueValues.add(value.trim());
            }
        }
        return uniqueValues.stream()
                .map(value -> {
                    String originalSource = extractedCandidates.stream()
                            .filter(candidate -> candidate.text().equals(value))
                            .map(KeywordCandidate::source)
                            .findFirst()
                            .orElse("");
                    String confirmedSource = originalSource.isEmpty()
                            ? "人工确认"
                            : "人工确认·" + originalSource;
                    return new KeywordCandidate(value, confirmedSource, 100);
                })
                .toList();
    }

    private static String normalize(String value, String fieldName) {
        String normalizedValue = value == null ? "" : value.trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalizedValue;
    }
}
