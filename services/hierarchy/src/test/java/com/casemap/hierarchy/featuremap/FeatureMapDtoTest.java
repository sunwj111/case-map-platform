package com.casemap.hierarchy.featuremap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureMapDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializePrototypeSample() throws Exception {
        FeatureMapDto map = loadSample();
        assertEquals("fm_quantity_price", map.getMeta().getMapId());
        assertEquals("家装/报价/金额计算与汇总/数量价汇总", map.getMeta().getFeatureKey());
        assertEquals("数量价汇总", map.getSpine().getFeatureName());
        assertEquals("金额计算与汇总", map.getSpine().getSceneName());
        assertEquals(3, map.getSpine().getNeighborFeatures().size());
        assertEquals(3, map.getBusinessView().getScenarios().size());
        assertEquals(4, map.getBusinessView().getCases().size());
        assertEquals(2, map.getTechView().getApis().size());
        assertTrue(map.getTechView().getFlowNodes().get(0).isPrimary());
        assertEquals(42, map.getSummary().getLinkedCaseCount());
        assertEquals(0.78, map.getSummary().getAutomationCoverage(), 0.0001);
        assertFalse(map.getConsumers().isEmpty());
    }

    @Test
    void roundTripKeepsRequiredSections() throws Exception {
        FeatureMapDto map = loadSample();
        String json = objectMapper.writeValueAsString(map);
        JsonNode root = objectMapper.readTree(json);
        assertTrue(root.has("meta"));
        assertTrue(root.has("spine"));
        assertTrue(root.has("businessView"));
        assertTrue(root.has("techView"));
        assertTrue(root.has("riskView"));
        assertTrue(root.has("summary"));
        assertTrue(root.path("techView").path("flowNodes").get(0).path("isPrimary").asBoolean());
    }

    @Test
    void projectUnifiedMapNodes() throws Exception {
        FeatureMapDto map = loadSample();
        List<MapNode> nodes = FeatureMapNodeProjector.project(map);
        assertTrue(nodes.stream().anyMatch(node -> node.getType() == MapNodeType.feature));
        assertTrue(nodes.stream().anyMatch(node -> node.getType() == MapNodeType.case_node));
        assertTrue(nodes.stream().anyMatch(node -> node.getType() == MapNodeType.api));
        assertTrue(nodes.stream().anyMatch(node -> node.getType() == MapNodeType.rule));
        MapNode caseNode = nodes.stream()
                .filter(node -> "case:TC-001".equals(node.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("标准方案数量价计算正确", caseNode.getTitle());
        assertEquals(92, caseNode.getConfidence());
        assertEquals(MapNodeStatus.ready, caseNode.getStatus());
        assertNotNull(caseNode.getPayload());
    }

    private FeatureMapDto loadSample() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/featuremap/quantity_price_sample.json")) {
            assertNotNull(stream);
            return objectMapper.readValue(stream, FeatureMapDto.class);
        }
    }
}
