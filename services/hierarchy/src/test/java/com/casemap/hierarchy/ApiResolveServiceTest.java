package com.casemap.hierarchy;

import com.casemap.hierarchy.tech.ApiResolveService;
import com.casemap.hierarchy.tech.FeatureApiMapCatalog;
import com.casemap.hierarchy.tech.FlowNodeCatalog;
import com.casemap.hierarchy.tech.ResolvedApi;
import com.casemap.hierarchy.tech.TechMappingResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResolveServiceTest {

    private ApiResolveService service;
    private FeatureApiMapCatalog apiMapCatalog;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        apiMapCatalog = new FeatureApiMapCatalog(mapper);
        service = new ApiResolveService(apiMapCatalog, new FlowNodeCatalog(mapper));
    }

    @Test
    void hitByFeatureNameAndPrimaryApiFirst() {
        TechMappingResult result = service.resolve(null, "数量价汇总", "金额计算与汇总");
        assertFalse(result.isFallback());
        assertEquals("jz_quotation", result.getService());
        assertTrue(result.getApis().size() >= 2);
        assertEquals("主接口", result.getApis().get(0).getRelation());
        assertEquals("/quotation/offer", result.getApis().get(0).getPath());
        assertTrue(result.getApis().stream().anyMatch(api -> "/quotation/bim/offer".equals(api.getPath())));
        assertTrue(result.getFlowNodes().stream().anyMatch(node -> "金额计算与汇总".equals(node.getName())));
        assertTrue(result.getFlowNodes().stream().anyMatch(node ->
                "金额计算与汇总".equals(node.getName()) && node.isPrimary() && "高".equals(node.getRisk())));
    }

    @Test
    void featureApiMapFindsQuantityPrice() {
        assertFalse(apiMapCatalog.findByFeatureName("数量价汇总").isEmpty());
        assertTrue(apiMapCatalog.findByFeatureName("数量价汇总").stream()
                .allMatch(item -> item.getFeatures().contains("数量价汇总")));
    }

    @Test
    void mergeFlowNodesDedupesCatalogAndMap() {
        TechMappingResult result = service.resolve("家装/报价/造价提交与审核/自动审核判定", null, null);
        long submitNodes = result.getFlowNodes().stream()
                .filter(node -> "造价提交与审核".equals(node.getName()))
                .count();
        assertEquals(1, submitNodes);
        assertTrue(result.getApis().stream().anyMatch(api -> "/quotation/submitDesignCost".equals(api.getPath())
                || "/quotation/bim/kysx/offer".equals(api.getPath())
                || "/quotation/confirmDesignCost".equals(api.getPath())));
        assertTrue(result.getFlowNodes().stream().anyMatch(node ->
                "造价提交与审核".equals(node.getName()) && node.getDescription() != null && !node.getDescription().isBlank()));
    }

    @Test
    void unknownFeatureReturnsFallbackApi() {
        TechMappingResult result = service.resolve(null, "不存在的功能点XYZ", null);
        assertTrue(result.isFallback());
        assertEquals(1, result.getApis().size());
        ResolvedApi fallback = result.getApis().get(0);
        assertTrue(fallback.isFallback());
        assertEquals("/quotation/offer", fallback.getPath());
        assertEquals("回退接口", fallback.getRelation());
    }

    @Test
    void flowNodeCatalogLoadsQuoteConfig() {
        assertEquals(11, service.listFlowNodes().size());
        assertTrue(service.listFlowNodes().stream().anyMatch(node -> "金额计算与汇总".equals(node.getNode())));
    }
}
