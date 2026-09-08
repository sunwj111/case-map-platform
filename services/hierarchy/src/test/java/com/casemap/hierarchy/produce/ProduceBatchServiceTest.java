package com.casemap.hierarchy.produce;

import com.casemap.hierarchy.asset.CaseAssetStore;
import com.casemap.hierarchy.store.HierarchyStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProduceBatchServiceTest {

    @TempDir
    Path tempDirectory;

    private ObjectMapper objectMapper;
    private Path batchFile;
    private ProduceBatchService produceBatchService;
    private ReviewQueueStore reviewQueueStore;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        batchFile = tempDirectory.resolve("produce/import_batches.json");

        HierarchyStore hierarchyStore = new HierarchyStore(
                objectMapper,
                tempDirectory.resolve("hierarchy.json").toString()
        );
        hierarchyStore.init();

        ImportBatchStore importBatchStore = new ImportBatchStore(objectMapper, batchFile.toString());
        importBatchStore.init();
        reviewQueueStore = new ReviewQueueStore(
                objectMapper,
                tempDirectory.resolve("produce/review_queue.json").toString()
        );
        reviewQueueStore.init();
        ReviewService reviewService = new ReviewService(
                reviewQueueStore,
                importBatchStore,
                new CaseAssetStore(objectMapper)
        );
        XMindFileParser xMindFileParser = new XMindFileParser(objectMapper);
        produceBatchService = new ProduceBatchService(
                hierarchyStore,
                importBatchStore,
                new CaseFileParser(10000, xMindFileParser),
                new KnowledgeFileParser(xMindFileParser),
                new NaturalLanguageKnowledgeExtractor(),
                new CaseCleaningService(),
                reviewService
        );
    }

    @Test
    void createsStandardBatchWithDualInputGate() {
        ImportBatch batch = produceBatchService.create(standardRequest(" 家装 ", " 报价 "));

        assertEquals(ProductionMode.STANDARD, batch.getMode());
        assertEquals("家装", batch.getDomain());
        assertEquals("报价", batch.getSystem());
        assertTrue(batch.isCasesRequired());
        assertTrue(batch.isKnowledgeRequired());
        assertEquals(
                ImportBatchStatus.WAITING_FOR_CASES_AND_KNOWLEDGE,
                batch.getStatus()
        );
        assertEquals(2, batch.getMissingInputs().size());
        assertFalse(batch.isReadyForKeywordCalibration());
        assertFalse(batch.isReadyForMapDraft());
    }

    @Test
    void advancesOnlyAfterCasesKnowledgeAndKeywordConfirmation() {
        ImportBatch batch = produceBatchService.create(standardRequest("家装", "报价"));
        String csv = "用例编号,用例名称,步骤,预期结果,场景,功能点\n"
                + "TC-001,数量价汇总,提交报价,金额正确,金额计算与汇总,数量价汇总\n";
        String knowledge = "场景：金额计算与汇总\n功能点：数量价汇总\n节点：报价计算节点";

        produceBatchService.importCases(batch.getId(), "cases.csv", csv.getBytes(StandardCharsets.UTF_8));
        ImportBatch afterCases = produceBatchService.get(batch.getId());
        assertEquals(ImportBatchStatus.WAITING_FOR_KNOWLEDGE, afterCases.getStatus());
        assertThrows(
                IllegalArgumentException.class,
                () -> produceBatchService.confirmKeywords(batch.getId())
        );

        produceBatchService.importKnowledge(
                batch.getId(),
                "knowledge.md",
                knowledge.getBytes(StandardCharsets.UTF_8)
        );
        ImportBatch afterKnowledge = produceBatchService.get(batch.getId());
        assertEquals(ImportBatchStatus.READY_FOR_KEYWORD_CALIBRATION, afterKnowledge.getStatus());
        assertTrue(afterKnowledge.isReadyForKeywordCalibration());
        assertThrows(
                IllegalArgumentException.class,
                () -> produceBatchService.confirmKeywords(batch.getId())
        );

        produceBatchService.extractKnowledge(batch.getId());
        ImportBatch afterConfirmation = produceBatchService.confirmKeywords(batch.getId());
        assertEquals(ImportBatchStatus.READY_FOR_MAP_DRAFT, afterConfirmation.getStatus());
        assertTrue(afterConfirmation.isReadyForMapDraft());

        MapDraftResult mapDraft = produceBatchService.generateMapDraft(batch.getId());
        ImportBatch generated = produceBatchService.get(batch.getId());
        assertEquals(ImportBatchStatus.REVIEW_IN_PROGRESS, generated.getStatus());
        assertTrue(generated.isReviewQueueGenerated());
        assertEquals(1, mapDraft.stats().totalCases());
        assertEquals("数量价汇总", mapDraft.cases().get(0).feature());
        assertEquals(1, reviewQueueStore.listAll().size());
    }

    @Test
    void persistsBatchAndReloadsGateState() throws Exception {
        ImportBatch batch = produceBatchService.create(standardRequest("家装", "报价"));
        String csv = "用例编号,用例名称,步骤,预期结果\nTC-001,数量价汇总,提交报价,金额正确\n";
        produceBatchService.importCases(
                batch.getId(),
                "cases.csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        ImportBatchStore reloadedStore = new ImportBatchStore(objectMapper, batchFile.toString());
        reloadedStore.init();
        ImportBatch reloaded = reloadedStore.get(batch.getId()).orElseThrow();

        assertTrue(reloaded.isCasesImported());
        assertFalse(reloaded.isKnowledgeImported());
        assertEquals(ImportBatchStatus.WAITING_FOR_KNOWLEDGE, reloaded.getStatus());
    }

    @Test
    void parsedFilesAdvanceStandardModeGateAndRemainTraceable() throws Exception {
        ImportBatch batch = produceBatchService.create(standardRequest("家装", "报价"));
        String csv = "用例编号,用例名称,步骤,预期结果\nTC-001,数量价汇总,提交报价,金额正确\n";
        String knowledge = "# 金额计算与汇总\n\n- 功能点：数量价汇总\n";

        CaseFileParseResult caseResult = produceBatchService.importCases(
                batch.getId(),
                "cases.csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );
        KnowledgeFileParseResult knowledgeResult = produceBatchService.importKnowledge(
                batch.getId(),
                "knowledge.md",
                knowledge.getBytes(StandardCharsets.UTF_8)
        );

        ImportBatch updatedBatch = produceBatchService.get(batch.getId());
        assertEquals(1, caseResult.rows().size());
        assertTrue(knowledgeResult.text().contains("数量价汇总"));
        assertTrue(updatedBatch.isReadyForKeywordCalibration());
        assertEquals("cases.csv", updatedBatch.getCaseImport().metadata().fileName());
        assertEquals("knowledge.md", updatedBatch.getKnowledgeImport().metadata().fileName());

        ImportBatchStore reloadedStore = new ImportBatchStore(objectMapper, batchFile.toString());
        reloadedStore.init();
        ImportBatch reloadedBatch = reloadedStore.get(batch.getId()).orElseThrow();
        assertEquals("TC-001", reloadedBatch.getCaseImport().rows().get(0).originalCaseId());
        assertTrue(reloadedBatch.getKnowledgeImport().text().contains("数量价汇总"));
    }

    @Test
    void pastedKnowledgeIsExtractedAndPersisted() throws Exception {
        ImportBatch batch = produceBatchService.create(standardRequest("家装", "报价"));
        String knowledge = """
                场景：金额计算与汇总
                功能点：数量价汇总
                规则：数量必须大于零
                节点：报价计算节点
                """;

        KnowledgeExtractionResult extractionResult = produceBatchService.importKnowledgeText(
                batch.getId(),
                "报价规则粘贴",
                knowledge
        );

        assertEquals("nl", extractionResult.mode());
        assertFalse(extractionResult.features().isEmpty());
        ImportBatch updatedBatch = produceBatchService.get(batch.getId());
        assertTrue(updatedBatch.isKnowledgeImported());
        assertEquals("报价规则粘贴", updatedBatch.getKnowledgeImport().metadata().fileName());
        assertEquals(
                updatedBatch.getKnowledgeImport().metadata().sha256(),
                updatedBatch.getKeywordExtraction().sourceVersion()
        );

        ImportBatchStore reloadedStore = new ImportBatchStore(objectMapper, batchFile.toString());
        reloadedStore.init();
        assertFalse(reloadedStore.get(batch.getId()).orElseThrow().getKeywordExtraction().rules().isEmpty());
    }

    @Test
    void pastedKnowledgeExtendsUploadedFileInsteadOfReplacingIt() {
        ImportBatch batch = produceBatchService.create(standardRequest("家装", "报价"));
        String uploadedKnowledge = "# 功能点\n- 自动审核判定\n";
        produceBatchService.importKnowledge(
                batch.getId(),
                "uploaded.md",
                uploadedKnowledge.getBytes(StandardCharsets.UTF_8)
        );

        produceBatchService.importKnowledgeText(
                batch.getId(),
                "人工补充",
                "规则：金额必须大于零"
        );

        ImportBatch updatedBatch = produceBatchService.get(batch.getId());
        assertTrue(updatedBatch.getKnowledgeImport().text().contains("自动审核判定"));
        assertTrue(updatedBatch.getKnowledgeImport().text().contains("金额必须大于零"));
        assertEquals("combined-knowledge", updatedBatch.getKnowledgeImport().metadata().format());
        assertFalse(updatedBatch.getKeywordExtraction().features().isEmpty());
        assertFalse(updatedBatch.getKeywordExtraction().rules().isEmpty());
    }

    @Test
    void rejectsUnsupportedModeAndUnknownTarget() {
        CreateImportBatchRequest casesOnlyRequest = standardRequest("家装", "报价");
        casesOnlyRequest.setMode(ProductionMode.CASES_ONLY);
        assertThrows(
                IllegalArgumentException.class,
                () -> produceBatchService.create(casesOnlyRequest)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> produceBatchService.create(standardRequest("未知领域", "报价"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> produceBatchService.create(standardRequest("家装", "未知系统"))
        );
    }

    private static CreateImportBatchRequest standardRequest(String domain, String system) {
        CreateImportBatchRequest request = new CreateImportBatchRequest();
        request.setDomain(domain);
        request.setSystem(system);
        request.setMode(ProductionMode.STANDARD);
        return request;
    }
}
