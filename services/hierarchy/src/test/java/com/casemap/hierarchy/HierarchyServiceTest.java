package com.casemap.hierarchy;

import com.casemap.hierarchy.featurekey.FeatureKey;
import com.casemap.hierarchy.featurekey.FeatureKeyException;
import com.casemap.hierarchy.model.CreateNodeRequest;
import com.casemap.hierarchy.model.NodeLevel;
import com.casemap.hierarchy.model.UpdateNodeRequest;
import com.casemap.hierarchy.store.HierarchyStore;
import com.casemap.hierarchy.store.SeedData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HierarchyServiceTest {

    @TempDir
    Path tempDir;

    private HierarchyStore store;

    @BeforeEach
    void setUp() throws Exception {
        store = new HierarchyStore(new ObjectMapper(), tempDir.resolve("store.json").toString());
        store.replaceAll(SeedData.quoteNodes());
    }

    @Test
    void featureKeyJoinAndParse() {
        String key = FeatureKey.join("家装", "报价", "金额计算与汇总", "数量价汇总");
        assertEquals("家装/报价/金额计算与汇总/数量价汇总", key);
        assertEquals("家装", FeatureKey.parse(key).domain());
    }

    @Test
    void featureKeyRejectSlash() {
        assertThrows(FeatureKeyException.class,
                () -> FeatureKey.join("家装", "报/价", "场景", "功能点"));
    }

    @Test
    void featureKeyUrlRoundTrip() {
        String key = "家装/报价/金额计算与汇总/数量价汇总";
        String encoded = FeatureKey.encodeForUrl(key);
        assertTrue(!encoded.contains("/"));
        assertEquals(key, FeatureKey.decodeFromUrl(encoded));
        assertEquals(key, FeatureKey.fromPathVariable(key));
        assertEquals(key, FeatureKey.fromPathVariable("/" + key));
        assertEquals(key, FeatureKey.fromPathVariable(encoded));
    }

    @Test
    void seedAndCascade() {
        assertTrue(store.getByFeatureKey("家装/报价/金额计算与汇总/数量价汇总").isPresent());
        assertTrue(store.cascadeOptions(null, null, null).getDomains().contains("家装"));
        assertTrue(store.cascadeOptions("家装", null, null).getSystems().contains("报价"));
        assertTrue(store.cascadeOptions("家装", "报价", null).getScenes().contains("金额计算与汇总"));
        assertTrue(store.cascadeOptions("家装", "报价", "金额计算与汇总").getFeatures().stream()
                .anyMatch(item -> "数量价汇总".equals(item.get("name"))));
    }

    @Test
    void createFeatureAndRenameCascade() {
        var scene = store.listNodes(NodeLevel.scene, null, null, false).stream()
                .filter(node -> "金额计算与汇总".equals(node.getName()))
                .findFirst()
                .orElseThrow();
        CreateNodeRequest create = new CreateNodeRequest();
        create.setLevel(NodeLevel.feature);
        create.setName("租赁价汇总");
        create.setParentId(scene.getId());
        var created = store.create(create);
        assertEquals("家装/报价/金额计算与汇总/租赁价汇总", created.getFeatureKey());

        UpdateNodeRequest update = new UpdateNodeRequest();
        update.setName("金额汇总");
        store.update(scene.getId(), update);
        assertTrue(store.getByFeatureKey("家装/报价/金额汇总/数量价汇总").isPresent());
    }
}
