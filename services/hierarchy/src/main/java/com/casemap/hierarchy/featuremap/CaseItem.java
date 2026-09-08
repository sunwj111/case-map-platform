package com.casemap.hierarchy.featuremap;

public class CaseItem {

    private String id;
    private String name;
    private String priority;
    private String testScenario;
    private Integer confidence;
    private String mountAdvice;
    private String api;
    private String originalCaseId;
    private String step;
    private String expected;
    private String flowNode;
    private String sourceType;
    private String source;

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

    public String getOriginalCaseId() {
        return originalCaseId;
    }

    public void setOriginalCaseId(String originalCaseId) {
        this.originalCaseId = originalCaseId;
    }

    public String getStep() {
        return step;
    }

    public void setStep(String step) {
        this.step = step;
    }

    public String getExpected() {
        return expected;
    }

    public void setExpected(String expected) {
        this.expected = expected;
    }

    public String getFlowNode() {
        return flowNode;
    }

    public void setFlowNode(String flowNode) {
        this.flowNode = flowNode;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
