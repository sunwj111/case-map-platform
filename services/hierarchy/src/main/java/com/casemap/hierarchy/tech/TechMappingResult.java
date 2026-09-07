package com.casemap.hierarchy.tech;

import java.util.ArrayList;
import java.util.List;

public class TechMappingResult {

    private String feature;
    private String scene;
    private String source;
    private String service;
    private String app;
    private boolean fallback;
    private List<ResolvedApi> apis = new ArrayList<>();
    private List<ResolvedFlowNode> flowNodes = new ArrayList<>();

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public boolean isFallback() {
        return fallback;
    }

    public void setFallback(boolean fallback) {
        this.fallback = fallback;
    }

    public List<ResolvedApi> getApis() {
        return apis;
    }

    public void setApis(List<ResolvedApi> apis) {
        this.apis = apis;
    }

    public List<ResolvedFlowNode> getFlowNodes() {
        return flowNodes;
    }

    public void setFlowNodes(List<ResolvedFlowNode> flowNodes) {
        this.flowNodes = flowNodes;
    }
}
