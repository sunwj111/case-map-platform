package com.casemap.hierarchy.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CascadeOptions {

    private List<String> domains = new ArrayList<>();
    private List<String> systems = new ArrayList<>();
    private List<String> scenes = new ArrayList<>();
    private List<Map<String, String>> features = new ArrayList<>();

    public List<String> getDomains() {
        return domains;
    }

    public void setDomains(List<String> domains) {
        this.domains = domains;
    }

    public List<String> getSystems() {
        return systems;
    }

    public void setSystems(List<String> systems) {
        this.systems = systems;
    }

    public List<String> getScenes() {
        return scenes;
    }

    public void setScenes(List<String> scenes) {
        this.scenes = scenes;
    }

    public List<Map<String, String>> getFeatures() {
        return features;
    }

    public void setFeatures(List<Map<String, String>> features) {
        this.features = features;
    }
}
