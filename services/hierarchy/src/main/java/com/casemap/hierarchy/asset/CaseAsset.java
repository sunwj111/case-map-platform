package com.casemap.hierarchy.asset;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CaseAsset {

    public static final String STATUS_CONFIRMED = "已确认";
    public static final String LIFECYCLE_ARCHIVED = "已归档";

    private String id;
    private String name;
    private String priority;
    private String testScenario;
    private String featureName;
    private String sceneName;
    private String featureKey;
    private String status;
    private String lifecycle;
    private String api;
    private Integer confidence;
    private String step;
    private String expected;
    private String sourceBatchId;
    private String sourceReviewId;
    private String originalCaseId;
    private String knowledgeSourceVersion;
    private String sourceType;
    private String flowNode;
    private String createdAt;
    private String updatedAt;

    @JsonIgnore
    public boolean isPublishedOfficial() {
        return STATUS_CONFIRMED.equals(status) && !LIFECYCLE_ARCHIVED.equals(lifecycle);
    }

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

    public String getFeatureName() {
        return featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }

    public String getSceneName() {
        return sceneName;
    }

    public void setSceneName(String sceneName) {
        this.sceneName = sceneName;
    }

    public String getFeatureKey() {
        return featureKey;
    }

    public void setFeatureKey(String featureKey) {
        this.featureKey = featureKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(String lifecycle) {
        this.lifecycle = lifecycle;
    }

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public Integer getConfidence() {
        return confidence;
    }

    public void setConfidence(Integer confidence) {
        this.confidence = confidence;
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

    public String getSourceBatchId() {
        return sourceBatchId;
    }

    public void setSourceBatchId(String sourceBatchId) {
        this.sourceBatchId = sourceBatchId;
    }

    public String getSourceReviewId() {
        return sourceReviewId;
    }

    public void setSourceReviewId(String sourceReviewId) {
        this.sourceReviewId = sourceReviewId;
    }

    public String getOriginalCaseId() {
        return originalCaseId;
    }

    public void setOriginalCaseId(String originalCaseId) {
        this.originalCaseId = originalCaseId;
    }

    public String getKnowledgeSourceVersion() {
        return knowledgeSourceVersion;
    }

    public void setKnowledgeSourceVersion(String knowledgeSourceVersion) {
        this.knowledgeSourceVersion = knowledgeSourceVersion;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getFlowNode() {
        return flowNode;
    }

    public void setFlowNode(String flowNode) {
        this.flowNode = flowNode;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
