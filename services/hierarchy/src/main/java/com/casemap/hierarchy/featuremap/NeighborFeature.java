package com.casemap.hierarchy.featuremap;

public class NeighborFeature {

    private String featureKey;
    private String featureName;

    public NeighborFeature() {
    }

    public NeighborFeature(String featureKey, String featureName) {
        this.featureKey = featureKey;
        this.featureName = featureName;
    }

    public String getFeatureKey() {
        return featureKey;
    }

    public void setFeatureKey(String featureKey) {
        this.featureKey = featureKey;
    }

    public String getFeatureName() {
        return featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }
}
