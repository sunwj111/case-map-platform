package com.casemap.hierarchy.featuremap;

import com.casemap.hierarchy.asset.CaseAssetStore;
import com.casemap.hierarchy.asset.CaseQueryService;
import com.casemap.hierarchy.knowledge.QuoteKnowledgeCatalog;
import com.casemap.hierarchy.store.HierarchyStore;
import com.casemap.hierarchy.store.SeedData;
import com.casemap.hierarchy.tech.ApiResolveService;
import com.casemap.hierarchy.tech.FeatureApiMapCatalog;
import com.casemap.hierarchy.tech.FlowNodeCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureMapAssemblerTest {

    @TempDir
    Path tempDir;

    private FeatureMapAssembler assembler;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HierarchyStore hierarchyStore = new HierarchyStore(mapper, tempDir.resolve("store.json").toString());
        hierarchyStore.replaceAll(SeedData.quoteNodes());
        assembler = newAssembler(hierarchyStore, mapper);
    }

    @Test
    void assembleCompleteDtoForKnownFeature() {
        FeatureMapDto map = assembler.assemble("家装/报价/金额计算与汇总/数量价汇总");

        assertNotNull(map.getMeta());
        assertNotNull(map.getSpine());
        assertNotNull(map.getBusinessView());
        assertNotNull(map.getTechView());
        assertNotNull(map.getRiskView());
        assertNotNull(map.getSummary());

        assertEquals("家装/报价/金额计算与汇总/数量价汇总", map.getMeta().getFeatureKey());
        assertTrue(map.getMeta().getDataSources().stream().anyMatch(source -> source.contains("正式资产") || source.contains("hierarchy")));

        assertEquals("家装", map.getSpine().getDomain());
        assertEquals("报价", map.getSpine().getApp());
        assertEquals("数量价汇总", map.getSpine().getFeatureName());
        assertEquals("金额计算与汇总", map.getSpine().getSceneName());
        assertFalse(map.getSpine().getValueTags().isEmpty());
        assertTrue(map.getSpine().getNeighborFeatures().stream()
                .anyMatch(item -> "固定价汇总".equals(item.getFeatureName())));

        assertEquals(3, map.getBusinessView().getCases().size());
        assertFalse(map.getBusinessView().getScenarios().isEmpty());
        assertFalse(map.getTechView().getApis().isEmpty());
        assertEquals(3, map.getSummary().getLinkedCaseCount());
        assertEquals(2, map.getSummary().getPriorityDistribution().get("P0"));
        assertEquals(1, map.getSummary().getPriorityDistribution().get("P1"));
        assertEquals("official-case-assets", map.getSummary().getMetricSource());
        assertEquals("unavailable", map.getSummary().getDefectSource());
        assertEquals(0, map.getSummary().getDefectCount30d());
        assertFalse(map.getMeta().isSynthetic());
    }

    @Test
    void businessViewFillsScriptsTemplatesAndUnavailableExecutions() {
        FeatureMapDto map = assembler.assemble("家装/报价/金额计算与汇总/数量价汇总");
        BusinessView business = map.getBusinessView();

        assertEquals(3, business.getScripts().size());
        assertTrue(business.getScripts().stream().allMatch(script -> "derived".equals(script.getStatus())));
        assertTrue(business.getScripts().stream().allMatch(script -> "derived-from-official-case".equals(script.getSource())));
        assertTrue(business.getScripts().stream().anyMatch(script -> "quotation_offer.json".equals(script.getName())));

        assertEquals(business.getScenarios().size(), business.getDataTemplates().size());
        assertTrue(business.getDataTemplates().stream().allMatch(template -> template.getLinkedScenarioId() != null));
        assertTrue(business.getDataTemplates().stream().allMatch(template -> "derived-from-official-case".equals(template.getSource())));

        assertEquals(1, business.getExecutions().size());
        ExecutionItem execution = business.getExecutions().get(0);
        assertEquals("unavailable", execution.getSource());
        assertTrue(execution.getResults().isEmpty());
        assertEquals("执行平台未接入", execution.getLabel());
        assertTrue(business.getCases().stream().allMatch(item -> "official-case-assets".equals(item.getSource())));
    }

    @Test
    void riskAndTechViewsComeFromOfficialKnowledge() {
        FeatureMapDto map = assembler.assemble("家装/报价/金额计算与汇总/数量价汇总");

        assertTrue(map.getRiskView().getRules().stream().anyMatch(rule -> "R002".equals(rule.getId())));
        assertTrue(map.getRiskView().getRules().stream().anyMatch(rule -> "R014".equals(rule.getId())));
        assertTrue(map.getRiskView().getDefects().isEmpty());
        assertTrue(map.getRiskView().getTags().stream().anyMatch(tag -> "高风险".equals(tag.getLabel())));
        assertTrue(map.getTechView().getTables().contains("quotations_record"));
        assertTrue(map.getTechView().getTables().contains("offer_rule"));
        assertTrue(map.getTechView().getMessages().isEmpty());
        assertTrue(map.getTechView().getCodeModules().stream()
                .anyMatch(module -> "QuotationApi#offerQuotations".equals(module.getName())));
        assertTrue(map.getMeta().getDataSources().contains("configs/quote.json"));
        assertTrue(map.getMeta().getDataSources().contains("executions:unavailable"));
        assertTrue(map.getMeta().getDataSources().contains("defects:unavailable"));
    }

    @Test
    void missingAssetsDegradeWithoutThrowing() {
        FeatureMapDto map = assembler.assemble("家装/报价/参数解析与输入精度/参数必填校验");

        assertEquals("家装/报价/参数解析与输入精度/参数必填校验", map.getMeta().getFeatureKey());
        assertEquals(0, map.getSummary().getLinkedCaseCount());
        assertEquals("gap", map.getSummary().getCoverageStatus());
        assertTrue(map.getBusinessView().getCases().isEmpty());
        assertFalse(map.getBusinessView().getScenarios().isEmpty());
        assertEquals("gap", map.getBusinessView().getScenarios().get(0).getCoverageStatus());
        assertFalse(map.getBusinessView().getDataTemplates().isEmpty());
        assertEquals("derived-from-scene", map.getBusinessView().getDataTemplates().get(0).getSource());
        assertFalse(map.getBusinessView().getScripts().isEmpty());
        assertTrue(map.getBusinessView().getScripts().stream()
                .allMatch(script -> "derived-from-tech-mapping".equals(script.getSource())));
        assertFalse(map.getTechView().getApis().isEmpty());
        assertTrue(map.getRiskView().getRules().stream().anyMatch(rule -> "R008".equals(rule.getId())));
        assertTrue(map.getMeta().getDataSources().contains("hierarchy"));
        assertFalse(map.getMeta().isSynthetic());
    }

    @Test
    void syntheticMapWhenHierarchyMissingButOfficialCasesExist() {
        FeatureMapDto map = assembler.assemble("家装/报价/造价审核/人工审核");

        assertTrue(map.getMeta().isSynthetic());
        assertTrue(map.getMeta().getMapId().startsWith("fm_synth_"));
        assertEquals("1.0.0-synthetic", map.getMeta().getVersion());
        assertTrue(map.getMeta().getDataSources().contains("synthetic"));
        assertTrue(map.getMeta().getDataSources().contains("hierarchy:missing"));
        assertEquals("审核流程", map.getSpine().getProcessName());
        assertTrue(map.getSpine().getValueTags().contains("造价审核"));
        assertEquals(1, map.getBusinessView().getCases().size());
        assertEquals("CA-301", map.getBusinessView().getCases().get(0).getId());
        assertFalse(map.getTechView().getApis().isEmpty());
    }

    @Test
    void unknownHierarchyFeatureStillReturnsDto() {
        FeatureMapDto map = assembler.assemble("家装/报价/金额计算与汇总/不存在功能点XYZ");

        assertEquals("不存在功能点XYZ", map.getSpine().getFeatureName());
        assertTrue(map.getMeta().isSynthetic());
        assertTrue(map.getMeta().getDataSources().contains("hierarchy:missing")
                || map.getMeta().getDataSources().contains("tech-mapping:fallback")
                || map.getSummary().getLinkedCaseCount() == 0);
        assertNotNull(map.getTechView());
        assertNotNull(map.getBusinessView());
        assertNotNull(map.getSummary());
        assertFalse(map.getBusinessView().getExecutions().isEmpty());
    }

    private static FeatureMapAssembler newAssembler(HierarchyStore hierarchyStore, ObjectMapper mapper) throws Exception {
        QuoteKnowledgeCatalog knowledgeCatalog = new QuoteKnowledgeCatalog(mapper);
        return new FeatureMapAssembler(
                hierarchyStore,
                new CaseQueryService(new CaseAssetStore(mapper)),
                new ApiResolveService(new FeatureApiMapCatalog(mapper), new FlowNodeCatalog(mapper)),
                knowledgeCatalog,
                new QualitySummaryService(),
                new RuleRiskService(knowledgeCatalog)
        );
    }
}
