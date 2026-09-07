package com.casemap.hierarchy.produce;

import jakarta.validation.constraints.NotBlank;

public class KnowledgeTextRequest {

    @NotBlank
    private String text;

    private String sourceName = "pasted-knowledge.md";

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }
}
