package com.casemap.hierarchy.produce;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseCleaningServiceTest {

    private final CaseCleaningService caseCleaningService = new CaseCleaningService();

    @Test
    void cleansCasesCalculatesConfidenceAndCreatesKnowledgeGaps() {
        ImportBatch batch = new ImportBatch();
        batch.setId("batch-cleaning-001");
        batch.setMode(ProductionMode.STANDARD);
        batch.setCaseImport(new CaseFileParseResult(
                null,
                List.of(new ParsedCaseRow(
                        "TC-001",
                        "设计师提交造价审核",
                        "提交造价单",
                        "进入待审核",
                        "造价单审核",
                        "人工审核",
                        "",
                        2
                )),
                List.of()
        ));
        batch.setKeywordExtraction(new KnowledgeExtractionResult(
                List.of(new KeywordCandidate("造价单审核", "人工确认", 100)),
                List.of(
                        new KeywordCandidate("人工审核", "人工确认", 100),
                        new KeywordCandidate("自动审核", "人工确认", 100)
                ),
                List.of(),
                List.of(new KeywordCandidate("CostAuditApi", "人工确认", 100)),
                "confirmed",
                "source-version"
        ));
        batch.setCasesImported(true);
        batch.setKnowledgeImported(true);
        batch.setKeywordsConfirmed(true);
        batch.refreshGate();

        MapDraftResult result = caseCleaningService.generate(batch);

        assertEquals(1, result.cases().size());
        assertEquals("人工审核", result.cases().get(0).feature());
        assertTrue(result.cases().get(0).confidence() >= 85);
        assertEquals(1, result.gaps().size());
        assertEquals("自动审核", result.gaps().get(0).feature());
        assertEquals(1, result.stats().autoMounted());
    }
}
