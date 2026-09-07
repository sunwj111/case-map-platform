package com.casemap.hierarchy.featuremap;

import java.util.ArrayList;
import java.util.List;

public class TechView {

    private String source;
    private List<ServiceItem> services = new ArrayList<>();
    private List<FlowNodeItem> flowNodes = new ArrayList<>();
    private List<ApiItem> apis = new ArrayList<>();
    private List<String> tables = new ArrayList<>();
    private List<String> messages = new ArrayList<>();
    private List<CodeModuleItem> codeModules = new ArrayList<>();

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<ServiceItem> getServices() {
        return services;
    }

    public void setServices(List<ServiceItem> services) {
        this.services = services;
    }

    public List<FlowNodeItem> getFlowNodes() {
        return flowNodes;
    }

    public void setFlowNodes(List<FlowNodeItem> flowNodes) {
        this.flowNodes = flowNodes;
    }

    public List<ApiItem> getApis() {
        return apis;
    }

    public void setApis(List<ApiItem> apis) {
        this.apis = apis;
    }

    public List<String> getTables() {
        return tables;
    }

    public void setTables(List<String> tables) {
        this.tables = tables;
    }

    public List<String> getMessages() {
        return messages;
    }

    public void setMessages(List<String> messages) {
        this.messages = messages;
    }

    public List<CodeModuleItem> getCodeModules() {
        return codeModules;
    }

    public void setCodeModules(List<CodeModuleItem> codeModules) {
        this.codeModules = codeModules;
    }
}
