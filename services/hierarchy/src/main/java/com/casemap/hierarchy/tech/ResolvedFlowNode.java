package com.casemap.hierarchy.tech;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ResolvedFlowNode {

    private String name;
    private String risk;
    private String description;
    @JsonProperty("isPrimary")
    private boolean primary;
    private String source;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRisk() {
        return risk;
    }

    public void setRisk(String risk) {
        this.risk = risk;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
