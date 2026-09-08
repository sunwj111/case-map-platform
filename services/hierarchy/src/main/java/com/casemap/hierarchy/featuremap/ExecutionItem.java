package com.casemap.hierarchy.featuremap;

import java.util.ArrayList;
import java.util.List;

public class ExecutionItem {

    private String id;
    private String label;
    private List<String> results = new ArrayList<>();
    private String lastRunAt;
    private String source;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<String> getResults() {
        return results;
    }

    public void setResults(List<String> results) {
        this.results = results;
    }

    public String getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(String lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
