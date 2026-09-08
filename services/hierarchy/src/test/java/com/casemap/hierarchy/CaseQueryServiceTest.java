package com.casemap.hierarchy;

import com.casemap.hierarchy.asset.CaseAssetStore;
import com.casemap.hierarchy.asset.CaseQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseQueryServiceTest {

    private CaseQueryService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new CaseQueryService(new CaseAssetStore(new ObjectMapper()));
    }

    @Test
    void onlyReturnsConfirmedAndNotArchived() {
        CaseQueryService.CaseQueryResult result = service.query(null, "数量价汇总", "金额计算与汇总");
        assertEquals(3, result.getTotal());
        assertTrue(result.containsId("TC-001"));
        assertTrue(result.containsId("TC-002"));
        assertTrue(result.containsId("TC-003"));
        assertFalse(result.containsId("TC-004"));
        assertFalse(result.containsId("TC-005"));
        result.getItems().forEach(item -> {
            assertEquals("已确认", item.getStatus());
            assertFalse("已归档".equals(item.getLifecycle()));
        });
    }

    @Test
    void filterByFeatureAndTestScenario() {
        CaseQueryService.CaseQueryResult result = service.query(null, "数量价汇总", "边界校验");
        assertEquals(1, result.getTotal());
        assertEquals("TC-003", result.getItems().get(0).getId());
    }

    @Test
    void filterByFeatureKey() {
        CaseQueryService.CaseQueryResult result =
                service.query("家装/报价/金额计算与汇总/固定价汇总", null, null);
        assertEquals(2, result.getTotal());
        assertTrue(result.containsId("TC-101"));
        assertTrue(result.containsId("TC-102"));
    }

    @Test
    void discardedCaseExcluded() {
        CaseQueryService.CaseQueryResult result = service.query(null, "自动审核判定", null);
        assertTrue(result.containsId("TC-201"));
        assertFalse(result.containsId("TC-202"));
    }

    @Test
    void missingFeatureRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.query(null, null, "金额计算与汇总"));
    }

    @Test
    void queryDoesNotFallbackAcrossScenes() {
        CaseQueryService.CaseQueryResult result =
                service.query("家装/报价/造价提交与审核/人工审核", null, null);
        assertEquals(0, result.getTotal());
    }

    @Test
    void queryForMapUsesExactFeatureKeyFirst() {
        CaseQueryService.CaseQueryResult result =
                service.queryForMap("家装/报价/造价审核/人工审核");
        assertEquals(1, result.getTotal());
        assertTrue(result.containsId("CA-301"));
    }

    @Test
    void queryForMapFallsBackToSameSystemFeatureName() {
        CaseQueryService.CaseQueryResult result =
                service.queryForMap("家装/报价/造价提交与审核/人工审核");
        assertEquals(1, result.getTotal());
        assertTrue(result.containsId("CA-301"));
    }
}
