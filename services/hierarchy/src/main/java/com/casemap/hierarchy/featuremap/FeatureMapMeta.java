package com.casemap.hierarchy.featuremap;

import java.util.ArrayList;
import java.util.List;

public class FeatureMapMeta {

    private String mapId;
    private String featureKey;
    private String version;
    private String baseline;
    private String updatedAt;
    private String qualityOwner;
    private List<String> dataSources = new ArrayList<>();
    private boolean synthetic;

    public String getMapId() {
        return mapId;
    }

    public void setMapId(String mapId) {
        this.mapId = mapId;
    }

    public String getFeatureKey() {
        return featureKey;
    }

    public void setFeatureKey(String featureKey) {
        this.featureKey = featureKey;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getBaseline() {
        return baseline;
    }

    public void setBaseline(String baseline) {
        this.baseline = baseline;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getQualityOwner() {
        return qualityOwner;
    }

    public void setQualityOwner(String qualityOwner) {
        this.qualityOwner = qualityOwner;
    }

    public List<String> getDataSources() {
        return dataSources;
    }

    public void setDataSources(List<String> dataSources) {
        this.dataSources = dataSources;
    }

    public boolean isSynthetic() {
        return synthetic;
    }

    public void setSynthetic(boolean synthetic) {
        this.synthetic = synthetic;
    }
}
