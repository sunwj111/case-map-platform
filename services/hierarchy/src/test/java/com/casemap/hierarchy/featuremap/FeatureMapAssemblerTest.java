package com.casemap.hierarchy.featuremap;

import com.casemap.hierarchy.asset.CaseAssetStore;
import com.casemap.hierarchy.asset.CaseQueryService;
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
        CaseQueryService caseQueryService = new CaseQueryService(new CaseAssetStore(mapper));
        ApiResolveService apiResolveService = new ApiResolveService(
                new FeatureApiMapCatalog(mapper),
                new FlowNodeCatalog(mapper)
        );
        assembler = new FeatureMapAssembler(hierarchyStore, caseQueryService, apiResolveService);
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
    }

    @Test
    void missingAssetsDegradeWithoutThrowing() {
        FeatureMapDto map = assembler.assemble("家装/报价/参数解析与输入精度/参数必填校验");

        assertEquals("家装/报价/参数解析与输入精度/参数必填校验", map.getMeta().getFeatureKey());
        assertEquals(0, map.getSummary().getLinkedCaseCount());
        assertTrue(map.getBusinessView().getCases().isEmpty());
        assertFalse(map.getTechView().getApis().isEmpty());
        assertNotNull(map.getRiskView());
        assertTrue(map.getMeta().getDataSources().contains("hierarchy"));
    }

    @Test
    void unknownHierarchyFeatureStillReturnsDto() {
        FeatureMapDto map = assembler.assemble("家装/报价/金额计算与汇总/不存在功能点XYZ");

        assertEquals("不存在功能点XYZ", map.getSpine().getFeatureName());
        assertTrue(map.getMeta().getDataSources().contains("hierarchy:missing")
                || map.getMeta().getDataSources().contains("tech-mapping:fallback")
                || map.getSummary().getLinkedCaseCount() == 0);
        assertNotNull(map.getTechView());
        assertNotNull(map.getBusinessView());
        assertNotNull(map.getSummary());
    }
}
