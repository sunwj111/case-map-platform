package com.casemap.hierarchy.featuremap;

public class CaseItem {

    private String id;
    private String name;
    private String priority;
    private String testScenario;
    private Integer confidence;
    private String mountAdvice;
    private String api;

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

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getTestScenario() {
        return testScenario;
    }

    public void setTestScenario(String testScenario) {
        this.testScenario = testScenario;
    }

    public Integer getConfidence() {
        return confidence;
    }

    public void setConfidence(Integer confidence) {
        this.confidence = confidence;
    }

    public String getMountAdvice() {
        return mountAdvice;
    }

    public void setMountAdvice(String mountAdvice) {
        this.mountAdvice = mountAdvice;
    }

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }
}
