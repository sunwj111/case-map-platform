package com.casemap.hierarchy.produce;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalLanguageKnowledgeExtractorTest {

    private final NaturalLanguageKnowledgeExtractor extractor = new NaturalLanguageKnowledgeExtractor();

    @Test
    void extractsFourCandidateGroupsFromStructuredKnowledge() {
        String knowledge = """
                ## 业务场景
                - 金额计算与汇总

                ## 功能点
                - 数量价汇总
                - 固定价汇总

                ## 规则
                - 数量必须大于零

                ## 流程节点
                - 报价计算节点
                """;

        KnowledgeExtractionResult result = extractor.extract(knowledge, "source-version-001");

        assertEquals("structured+nl", result.mode());
        assertEquals("source-version-001", result.sourceVersion());
        assertTrue(result.scenes().stream().anyMatch(item -> "金额计算与汇总".equals(item.text())));
        assertTrue(result.features().stream().anyMatch(item -> "数量价汇总".equals(item.text())));
        assertTrue(result.rules().stream().anyMatch(item -> "数量必须大于零".equals(item.text())));
        assertTrue(result.nodes().stream().anyMatch(item -> "报价计算节点".equals(item.text())));
        assertTrue(result.rules().stream().allMatch(item -> item.confidence() >= 70));
    }

    @Test
    void extractsCandidatesFromPastedNaturalLanguage() {
        String knowledge = """
                场景：报价提交、自动审核
                功能点：数量价汇总、优惠后汇总
                节点：报价计算节点、审核节点
                规则：金额必须大于零；已归档报价不可修改
                """;

        KnowledgeExtractionResult result = extractor.extract(knowledge, "pasted-version");

        assertEquals("nl", result.mode());
        assertTrue(result.scenes().stream().anyMatch(item -> "报价提交".equals(item.text())));
        assertTrue(result.features().stream().anyMatch(item -> "优惠后汇总".equals(item.text())));
        assertTrue(result.nodes().stream().anyMatch(item -> "审核节点".equals(item.text())));
        assertFalse(result.rules().isEmpty());
    }

    @Test
    void extractsMarkdownTablesAndTechnicalCallChainWithoutSeparatorNoise() {
        String knowledge = """
                ## 二、总体调用链
                CostAuditApi → CostAuditRpcService → DesignCostService → DesignCostDao

                ## 三、涉及接口清单
                | 接口 | 方法 | 说明 |
                |---|---|---|
                | `/costAudit/findList` | `findList` | 查询造价审核列表 |
                | `/costAudit/accept` | `accept` | 接单 |
                """;

        KnowledgeExtractionResult result = extractor.extract(knowledge, "markdown-version");

        assertTrue(result.nodes().stream().anyMatch(item -> "CostAuditApi".equals(item.text())));
        assertTrue(result.nodes().stream().anyMatch(item -> "DesignCostService".equals(item.text())));
        assertTrue(result.features().stream().anyMatch(item -> "/costAudit/findList".equals(item.text())));
        assertTrue(result.features().stream().noneMatch(item -> item.text().contains("---")));
    }

    @Test
    void keepsShortNaturalLanguageAsFallbackFeature() {
        KnowledgeExtractionResult result = extractor.extract("造价审核接口", "short-version");

        assertEquals(1, result.features().size());
        assertEquals("造价审核接口", result.features().get(0).text());
    }

    @Test
    void rejectsBlankKnowledge() {
        assertThrows(
                IllegalArgumentException.class,
                () -> extractor.extract(" ", "source-version")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> extractor.extract("5", "source-version")
        );
    }
}
