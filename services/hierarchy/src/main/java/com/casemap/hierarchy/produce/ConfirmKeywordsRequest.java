package com.casemap.hierarchy.produce;

import java.util.ArrayList;
import java.util.List;

public class ConfirmKeywordsRequest {

    private List<String> scenes = new ArrayList<>();
    private List<String> features = new ArrayList<>();
    private List<String> rules = new ArrayList<>();
    private List<String> nodes = new ArrayList<>();

    public List<String> getScenes() {
        return scenes;
    }

    public void setScenes(List<String> scenes) {
        this.scenes = copy(scenes);
    }

    public List<String> getFeatures() {
        return features;
    }

    public void setFeatures(List<String> features) {
        this.features = copy(features);
    }

    public List<String> getRules() {
        return rules;
    }

    public void setRules(List<String> rules) {
        this.rules = copy(rules);
    }

    public List<String> getNodes() {
        return nodes;
    }

    public void setNodes(List<String> nodes) {
        this.nodes = copy(nodes);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
