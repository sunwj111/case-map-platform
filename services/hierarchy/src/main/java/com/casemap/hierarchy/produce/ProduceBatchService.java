package com.casemap.hierarchy.produce;

import com.casemap.hierarchy.model.CascadeOptions;
import com.casemap.hierarchy.store.HierarchyStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProduceBatchService {

    private final HierarchyStore hierarchyStore;
    private final ImportBatchStore importBatchStore;
    private final CaseFileParser caseFileParser;
    private final KnowledgeFileParser knowledgeFileParser;
    private final NaturalLanguageKnowledgeExtractor knowledgeExtractor;

    public ProduceBatchService(
            HierarchyStore hierarchyStore,
            ImportBatchStore importBatchStore,
            CaseFileParser caseFileParser,
            KnowledgeFileParser knowledgeFileParser,
            NaturalLanguageKnowledgeExtractor knowledgeExtractor
    ) {
        this.hierarchyStore = hierarchyStore;
        this.importBatchStore = importBatchStore;
        this.caseFileParser = caseFileParser;
        this.knowledgeFileParser = knowledgeFileParser;
        this.knowledgeExtractor = knowledgeExtractor;
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
        ensureStandardMode(batch);
        CaseFileParseResult parseResult = caseFileParser.parse(fileName, content);
        importBatchStore.update(batchId, currentBatch -> {
            currentBatch.setCaseImport(parseResult);
            currentBatch.setCasesImported(true);
            currentBatch.setKeywordsConfirmed(false);
            currentBatch.setMapDraftGenerated(false);
        });
        return parseResult;
    }

    public KnowledgeFileParseResult importKnowledge(String batchId, String fileName, byte[] content) {
        ImportBatch batch = get(batchId);
        ensureStandardMode(batch);
        KnowledgeFileParseResult parseResult = knowledgeFileParser.parse(fileName, content);
        importBatchStore.update(batchId, currentBatch -> {
            currentBatch.setKnowledgeImport(parseResult);
            currentBatch.setKnowledgeImported(true);
            currentBatch.setKeywordExtraction(null);
            currentBatch.setKeywordsConfirmed(false);
            currentBatch.setMapDraftGenerated(false);
        });
        return parseResult;
    }

    public KnowledgeExtractionResult extractKnowledge(String batchId) {
        ImportBatch batch = get(batchId);
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
        });
        return extractionResult;
    }

    public KnowledgeExtractionResult importKnowledgeText(
            String batchId,
            String sourceName,
            String text
    ) {
        ImportBatch batch = get(batchId);
        ensureStandardMode(batch);
        String normalizedText = text == null ? "" : text.trim();
        if (normalizedText.isEmpty()) {
            throw new IllegalArgumentException("知识文本不能为空");
        }
        byte[] content = normalizedText.getBytes(StandardCharsets.UTF_8);
        String normalizedSourceName = sourceName == null || sourceName.isBlank()
                ? "pasted-knowledge.md"
                : sourceName.trim();
        ImportFileMetadata metadata = new ImportFileMetadata(
                normalizedSourceName,
                "pasted-text",
                content.length,
                FileParsingSupport.sha256(content),
                null,
                null,
                null
        );
        KnowledgeFileParseResult parseResult = new KnowledgeFileParseResult(
                metadata,
                normalizedText,
                normalizedText.split("\\R", -1).length,
                List.of()
        );
        KnowledgeExtractionResult extractionResult = knowledgeExtractor.extract(
                normalizedText,
                metadata.sha256()
        );
        importBatchStore.update(batchId, currentBatch -> {
            currentBatch.setKnowledgeImport(parseResult);
            currentBatch.setKnowledgeImported(true);
            currentBatch.setKeywordExtraction(extractionResult);
            currentBatch.setKeywordsConfirmed(false);
            currentBatch.setMapDraftGenerated(false);
        });
        return extractionResult;
    }

    public ImportBatch markCasesImported(String batchId) {
        return importBatchStore.update(batchId, batch -> batch.setCasesImported(true));
    }

    public ImportBatch markKnowledgeImported(String batchId) {
        return importBatchStore.update(batchId, batch -> batch.setKnowledgeImported(true));
    }

    public ImportBatch confirmKeywords(String batchId) {
        return importBatchStore.update(batchId, batch -> {
            batch.refreshGate();
            if (!batch.isReadyForKeywordCalibration()) {
                throw new IllegalArgumentException("标准模式需先完成历史用例和知识库导入");
            }
            batch.setKeywordsConfirmed(true);
        });
    }

    public ImportBatch markMapDraftGenerated(String batchId) {
        return importBatchStore.update(batchId, batch -> {
            batch.refreshGate();
            if (!batch.isReadyForMapDraft()) {
                throw new IllegalArgumentException("需先完成双源导入并确认关键字");
            }
            batch.setMapDraftGenerated(true);
        });
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

    private static String normalize(String value, String fieldName) {
        String normalizedValue = value == null ? "" : value.trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalizedValue;
    }
}
