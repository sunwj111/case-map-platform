package com.casemap.hierarchy.featuremap;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FlowNodeItem {

    private String name;
    private String risk;
    @JsonProperty("isPrimary")
    private boolean primary;

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

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }
}
