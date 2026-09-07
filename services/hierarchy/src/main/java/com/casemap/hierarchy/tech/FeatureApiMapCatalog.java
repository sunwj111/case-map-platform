package com.casemap.hierarchy.tech;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class FeatureApiMapCatalog {

    private final FeatureApiMapFile file;

    public FeatureApiMapCatalog(ObjectMapper objectMapper) throws IOException {
        try (InputStream input = requiredResource("/data/feature_api_map.json")) {
            this.file = objectMapper.readValue(input, FeatureApiMapFile.class);
        }
    }

    public FeatureApiMapFile getFile() {
        return file;
    }

    public List<FeatureApiMapItem> listItems() {
        return Collections.unmodifiableList(file.getItems());
    }

    public List<FeatureApiMapItem> findByFeatureName(String featureName) {
        String feature = featureName == null ? "" : featureName.trim();
        if (feature.isEmpty()) {
            return List.of();
        }
        List<FeatureApiMapItem> matched = new ArrayList<>();
        for (FeatureApiMapItem item : file.getItems()) {
            if (hitsFeature(item, feature)) {
                matched.add(item);
            }
        }
        return matched;
    }

    static boolean hitsFeature(FeatureApiMapItem item, String feature) {
        for (String mapped : item.getFeatures()) {
            if (mapped == null) {
                continue;
            }
            if (mapped.equals(feature) || feature.contains(mapped) || mapped.contains(feature)) {
                return true;
            }
        }
        return false;
    }

    static boolean hitsNode(FeatureApiMapItem item, String scene) {
        if (scene == null || scene.isBlank()) {
            return false;
        }
        String primary = item.getPrimaryNode();
        if (scene.equals(primary)
                || (primary != null && (scene.contains(primary) || primary.contains(scene)))) {
            return true;
        }
        return item.getNodes().contains(scene);
    }

    private static InputStream requiredResource(String path) {
        InputStream input = FeatureApiMapCatalog.class.getResourceAsStream(path);
        if (input == null) {
            throw new IllegalStateException("缺少资源文件：" + path);
        }
        return input;
    }
}
