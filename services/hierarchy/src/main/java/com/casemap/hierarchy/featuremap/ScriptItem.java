package com.casemap.hierarchy.featuremap;

public class ScriptItem {

    private String id;
    private String name;
    private String type;
    private String status;
    private String linkedCaseId;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLinkedCaseId() {
        return linkedCaseId;
    }

    public void setLinkedCaseId(String linkedCaseId) {
        this.linkedCaseId = linkedCaseId;
    }
}
