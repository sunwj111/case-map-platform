package com.casemap.hierarchy.tech;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FeatureApiMapFile {

    private String domain;
    private String app;
    private String service;
    private String source;
    private List<FeatureApiMapItem> items = new ArrayList<>();

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

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<FeatureApiMapItem> getItems() {
        return items;
    }

    public void setItems(List<FeatureApiMapItem> items) {
        this.items = items;
    }
}
