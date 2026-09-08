package com.casemap.hierarchy.web;

import com.casemap.hierarchy.produce.ImportBatch;
import com.casemap.hierarchy.produce.ImportFileMetadata;
import com.casemap.hierarchy.produce.CaseFileParseResult;
import com.casemap.hierarchy.produce.KeywordCandidate;
import com.casemap.hierarchy.produce.KnowledgeExtractionResult;
import com.casemap.hierarchy.produce.MapDraftResult;
import com.casemap.hierarchy.produce.MapDraftStats;
import com.casemap.hierarchy.produce.ProduceBatchService;
import com.casemap.hierarchy.produce.ProductionMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProduceController.class)
class ProduceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProduceBatchService produceBatchService;

    @Test
    void createsStandardBatch() throws Exception {
        ImportBatch batch = standardBatch("batch-standard-001");
        when(produceBatchService.create(any())).thenReturn(batch);

        mockMvc.perform(post("/api/v1/produce/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "domain": "家装",
                                  "system": "报价",
                                  "mode": "standard"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("batch-standard-001"))
                .andExpect(jsonPath("$.mode").value("standard"))
                .andExpect(jsonPath("$.knowledgeRequired").value(true))
                .andExpect(jsonPath("$.missingInputs[0]").value("cases"))
                .andExpect(jsonPath("$.missingInputs[1]").value("knowledge"));
    }

    @Test
    void returnsNotFoundForUnknownBatch() throws Exception {
        when(produceBatchService.find("missing-batch")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/produce/batches/missing-batch"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("导入批次不存在：missing-batch"));
    }

    @Test
    void uploadsAndParsesCaseFile() throws Exception {
        ImportFileMetadata metadata = new ImportFileMetadata(
                "cases.csv",
                "csv",
                20,
                "hash",
                1,
                null,
                null
        );
        CaseFileParseResult parseResult = new CaseFileParseResult(metadata, List.of(), List.of());
        when(produceBatchService.find("batch-001")).thenReturn(Optional.of(standardBatch("batch-001")));
        when(produceBatchService.importCases(any(), any(), any())).thenReturn(parseResult);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cases.csv",
                "text/csv",
                "用例名称\n数量价汇总".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/produce/batches/batch-001/cases").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.fileName").value("cases.csv"))
                .andExpect(jsonPath("$.metadata.rowCount").value(1));
    }

    @Test
    void extractsPastedKnowledgeText() throws Exception {
        KnowledgeExtractionResult extractionResult = new KnowledgeExtractionResult(
                List.of(new KeywordCandidate("金额计算", "行首·场景", 85)),
                List.of(new KeywordCandidate("数量价汇总", "行首·功能点", 85)),
                List.of(),
                List.of(),
                "nl",
                "source-version"
        );
        when(produceBatchService.find("batch-001")).thenReturn(Optional.of(standardBatch("batch-001")));
        when(produceBatchService.importKnowledgeText(any(), any(), any())).thenReturn(extractionResult);

        mockMvc.perform(post("/api/v1/produce/batches/batch-001/knowledge/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceName": "粘贴知识",
                                  "text": "场景：金额计算\\n功能点：数量价汇总"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("nl"))
                .andExpect(jsonPath("$.features[0].text").value("数量价汇总"))
                .andExpect(jsonPath("$.features[0].confidence").value(85));
    }

    @Test
    void confirmsKeywordsAndGeneratesDraft() throws Exception {
        ImportBatch batch = standardBatch("batch-001");
        batch.setCasesImported(true);
        batch.setKnowledgeImported(true);
        batch.setKeywordsConfirmed(true);
        batch.refreshGate();
        when(produceBatchService.find("batch-001")).thenReturn(Optional.of(batch));
        when(produceBatchService.confirmKeywords(any(), any())).thenReturn(batch);
        when(produceBatchService.generateMapDraft("batch-001")).thenReturn(new MapDraftResult(
                "batch-001",
                List.of(),
                List.of(),
                new MapDraftStats(14, 10, 4, 0, 2),
                "2026-09-07T16:00:00Z"
        ));

        mockMvc.perform(post("/api/v1/produce/batches/batch-001/keywords/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scenes": ["造价单审核"],
                                  "features": ["人工审核"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyForMapDraft").value(true));

        mockMvc.perform(post("/api/v1/produce/batches/batch-001/draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.totalCases").value(14))
                .andExpect(jsonPath("$.stats.autoMounted").value(10));
    }

    private static ImportBatch standardBatch(String batchId) {
        ImportBatch batch = new ImportBatch();
        batch.setId(batchId);
        batch.setDomain("家装");
        batch.setSystem("报价");
        batch.setMode(ProductionMode.STANDARD);
        batch.refreshGate();
        return batch;
    }
}
