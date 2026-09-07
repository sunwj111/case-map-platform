package com.casemap.hierarchy.produce;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateImportBatchRequest {

    @NotBlank
    private String domain;

    @NotBlank
    private String system;

    @NotNull
    private ProductionMode mode = ProductionMode.STANDARD;

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

    public ProductionMode getMode() {
        return mode;
    }

    public void setMode(ProductionMode mode) {
        this.mode = mode;
    }
}
