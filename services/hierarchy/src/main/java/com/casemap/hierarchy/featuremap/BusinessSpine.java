package com.casemap.hierarchy.featuremap;

import java.util.ArrayList;
import java.util.List;

public class BusinessSpine {

    private String domain;
    private String app;
    private String processName;
    private String featureName;
    private String sceneName;
    private List<String> valueTags = new ArrayList<>();
    private List<NeighborFeature> neighborFeatures = new ArrayList<>();

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public String getFeatureName() {
        return featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }

    public String getSceneName() {
        return sceneName;
    }

    public void setSceneName(String sceneName) {
        this.sceneName = sceneName;
    }

    public List<String> getValueTags() {
        return valueTags;
    }

    public void setValueTags(List<String> valueTags) {
        this.valueTags = valueTags;
    }

    public List<NeighborFeature> getNeighborFeatures() {
        return neighborFeatures;
    }

    public void setNeighborFeatures(List<NeighborFeature> neighborFeatures) {
        this.neighborFeatures = neighborFeatures;
    }
}
