package com.casemap.hierarchy.tech;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FeatureApiMapItem {

    private String method;
    private List<String> paths = new ArrayList<>();
    private String primaryNode;
    private List<String> nodes = new ArrayList<>();
    private List<String> features = new ArrayList<>();
    private String relation;
    private String controller;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public String getPrimaryNode() {
        return primaryNode;
    }

    public void setPrimaryNode(String primaryNode) {
        this.primaryNode = primaryNode;
    }

    public List<String> getNodes() {
        return nodes;
    }

    public void setNodes(List<String> nodes) {
        this.nodes = nodes;
    }

    public List<String> getFeatures() {
        return features;
    }

    public void setFeatures(List<String> features) {
        this.features = features;
    }

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }

    public String getController() {
        return controller;
    }

    public void setController(String controller) {
        this.controller = controller;
    }
}
