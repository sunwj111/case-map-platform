package com.casemap.hierarchy.asset;

import com.casemap.hierarchy.featurekey.FeatureKey;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class CaseQueryService {

    private final CaseAssetStore store;

    public CaseQueryService(CaseAssetStore store) {
        this.store = store;
    }

    public CaseQueryResult query(String featureKey, String feature, String scene) {
        String resolvedFeature = trimToNull(feature);
        String resolvedScene = trimToNull(scene);
        String resolvedKey = trimToNull(featureKey);

        if (resolvedKey != null) {
            FeatureKey parsed = FeatureKey.parse(resolvedKey);
            resolvedKey = parsed.toKey();
            if (resolvedFeature == null) {
                resolvedFeature = parsed.feature();
            }
            if (resolvedScene == null) {
                resolvedScene = parsed.scene();
            }
        }
        if (resolvedFeature == null && resolvedKey == null) {
            throw new IllegalArgumentException("请提供 feature 或 featureKey");
        }

        List<CaseAsset> matched = new ArrayList<>();
        for (CaseAsset asset : store.listAll()) {
            if (!asset.isPublishedOfficial()) {
                continue;
            }
            if (!matchesFeature(asset, resolvedFeature, resolvedKey)) {
                continue;
            }
            if (!matchesScene(asset, resolvedScene)) {
                continue;
            }
            matched.add(asset);
        }
        return new CaseQueryResult(resolvedFeature, resolvedScene, resolvedKey, store.getSource(), matched);
    }

    private static boolean matchesFeature(CaseAsset asset, String feature, String featureKey) {
        if (featureKey != null && featureKey.equals(asset.getFeatureKey())) {
            return true;
        }
        if (feature == null) {
            return featureKey == null;
        }
        if (feature.equals(asset.getFeatureName())) {
            return true;
        }
        String assetKey = asset.getFeatureKey();
        return assetKey != null && assetKey.endsWith("/" + feature);
    }

    private static boolean matchesScene(CaseAsset asset, String scene) {
        if (scene == null) {
            return true;
        }
        return scene.equals(asset.getSceneName()) || scene.equals(asset.getTestScenario());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class CaseQueryResult {
        private final String feature;
        private final String scene;
        private final String featureKey;
        private final String source;
        private final List<CaseAsset> items;

        public CaseQueryResult(String feature, String scene, String featureKey, String source, List<CaseAsset> items) {
            this.feature = feature;
            this.scene = scene;
            this.featureKey = featureKey;
            this.source = source;
            this.items = items;
        }

        public String getFeature() {
            return feature;
        }

        public String getScene() {
            return scene;
        }

        public String getFeatureKey() {
            return featureKey;
        }

        public String getSource() {
            return source;
        }

        public int getTotal() {
            return items.size();
        }

        public List<CaseAsset> getItems() {
            return items;
        }

        public boolean containsId(String caseId) {
            return items.stream().anyMatch(item -> Objects.equals(caseId, item.getId()));
        }
    }
}
