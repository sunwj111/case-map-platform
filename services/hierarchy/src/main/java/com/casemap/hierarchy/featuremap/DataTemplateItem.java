package com.casemap.hierarchy.featuremap;

public class DataTemplateItem {

    private String id;
    private String name;
    private String linkedScenarioId;
    private String source;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLinkedScenarioId() {
        return linkedScenarioId;
    }

    public void setLinkedScenarioId(String linkedScenarioId) {
        this.linkedScenarioId = linkedScenarioId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
