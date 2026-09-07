package com.casemap.hierarchy.model;

import jakarta.validation.constraints.NotBlank;

public class FeatureKeyEncodeRequest {

    @NotBlank
    private String domain;
    @NotBlank
    private String system;
    @NotBlank
    private String scene;
    @NotBlank
    private String feature;

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }
}
