package com.casemap.hierarchy.tech;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class QuoteFlowNodeFile {

    private String domain;
    private String displayName;
    private String source;
    private List<QuoteFlowNode> flowNodes = new ArrayList<>();

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<QuoteFlowNode> getFlowNodes() {
        return flowNodes;
    }

    public void setFlowNodes(List<QuoteFlowNode> flowNodes) {
        this.flowNodes = flowNodes;
    }
}
