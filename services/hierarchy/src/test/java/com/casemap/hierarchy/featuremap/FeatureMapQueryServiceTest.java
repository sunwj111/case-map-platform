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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureMapQueryServiceTest {

    @TempDir
    Path tempDir;

    private FeatureMapQueryService queryService;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HierarchyStore hierarchyStore = new HierarchyStore(mapper, tempDir.resolve("store.json").toString());
        hierarchyStore.replaceAll(SeedData.quoteNodes());
        FeatureMapAssembler assembler = newAssembler(hierarchyStore, mapper);
        queryService = new FeatureMapQueryService(
                hierarchyStore,
                new CaseQueryService(new CaseAssetStore(mapper)),
                new FeatureApiMapCatalog(mapper),
                assembler
        );
    }

    @Test
    void knownFeatureReturnsCompleteMap() {
        FeatureMapDto map = queryService.findByFeatureKey("家装/报价/金额计算与汇总/数量价汇总").orElseThrow();

        assertEquals("家装/报价/金额计算与汇总/数量价汇总", map.getMeta().getFeatureKey());
        assertEquals("家装", map.getSpine().getDomain());
        assertEquals("报价", map.getSpine().getApp());
        assertEquals("金额计算与汇总", map.getSpine().getSceneName());
        assertEquals("数量价汇总", map.getSpine().getFeatureName());
        assertEquals(3, map.getBusinessView().getCases().size());
        assertFalse(map.getTechView().getApis().isEmpty());
        assertEquals(3, map.getSummary().getLinkedCaseCount());
        assertFalse(map.getMeta().isSynthetic());
    }

    @Test
    void missingAssetsStillReturnMapWhenFeatureExists() {
        FeatureMapDto map = queryService.findByFeatureKey("家装/报价/参数解析与输入精度/参数必填校验").orElseThrow();

        assertEquals(0, map.getSummary().getLinkedCaseCount());
        assertTrue(map.getBusinessView().getCases().isEmpty());
        assertFalse(map.getTechView().getApis().isEmpty());
    }

    @Test
    void syntheticCostAuditFeatureReturnsMap() {
        FeatureMapDto map = queryService.findByFeatureKey("家装/报价/造价审核/人工审核").orElseThrow();

        assertTrue(map.getMeta().isSynthetic());
        assertTrue(map.getMeta().getMapId().startsWith("fm_synth_"));
        assertEquals("1.0.0-synthetic", map.getMeta().getVersion());
        assertEquals("造价审核", map.getSpine().getSceneName());
        assertEquals("人工审核", map.getSpine().getFeatureName());
        assertEquals("审核流程", map.getSpine().getProcessName());
        assertEquals(1, map.getBusinessView().getCases().size());
        assertEquals("CA-301", map.getBusinessView().getCases().get(0).getId());
        assertFalse(map.getTechView().getApis().isEmpty());
    }

    @Test
    void sameSystemFeatureNameFallbackReturnsMap() {
        FeatureMapDto map = queryService.findByFeatureKey("家装/报价/造价提交与审核/人工审核").orElseThrow();

        assertTrue(map.getMeta().isSynthetic());
        assertEquals(1, map.getBusinessView().getCases().size());
        assertEquals("CA-301", map.getBusinessView().getCases().get(0).getId());
    }

    @Test
    void techMappingWithoutHierarchyStillReturnsMap() {
        FeatureMapDto map = queryService.findByFeatureKey("家装/报价/造价审核/转派").orElseThrow();

        assertTrue(map.getMeta().isSynthetic());
        assertTrue(map.getBusinessView().getCases().isEmpty());
        assertTrue(map.getTechView().getApis().stream()
                .anyMatch(api -> "/costAudit/transform".equals(api.getPath())));
    }

    @Test
    void unknownFeatureReturnsEmpty() {
        Optional<FeatureMapDto> result = queryService.findByFeatureKey("家装/报价/金额计算与汇总/不存在功能点XYZ");
        assertTrue(result.isEmpty());
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
