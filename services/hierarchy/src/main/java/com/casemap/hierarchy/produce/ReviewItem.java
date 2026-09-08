package com.casemap.hierarchy.produce;

import java.util.ArrayList;
import java.util.List;

public class ReviewItem {

    private String id;
    private String batchId;
    private ReviewItemKind kind;
    private ReviewStatus status;
    private String originalCaseId;
    private String officialCaseId;
    private String caseName;
    private String scene;
    private String feature;
    private String flowNode;
    private String step;
    private String expected;
    private String priority;
    private int confidence;
    private List<String> matchEvidence = new ArrayList<>();
    private List<ReviewOperation> operations = new ArrayList<>();
    private String createdAt;
    private String updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public ReviewItemKind getKind() {
        return kind;
    }

    public void setKind(ReviewItemKind kind) {
        this.kind = kind;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public String getOriginalCaseId() {
        return originalCaseId;
    }

    public void setOriginalCaseId(String originalCaseId) {
        this.originalCaseId = originalCaseId;
    }

    public String getOfficialCaseId() {
        return officialCaseId;
    }

    public void setOfficialCaseId(String officialCaseId) {
        this.officialCaseId = officialCaseId;
    }

    public String getCaseName() {
        return caseName;
    }

    public void setCaseName(String caseName) {
        this.caseName = caseName;
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

    public String getFlowNode() {
        return flowNode;
    }

    public void setFlowNode(String flowNode) {
        this.flowNode = flowNode;
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

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public int getConfidence() {
        return confidence;
    }

    public void setConfidence(int confidence) {
        this.confidence = confidence;
    }

    public List<String> getMatchEvidence() {
        return matchEvidence;
    }

    public void setMatchEvidence(List<String> matchEvidence) {
        this.matchEvidence = matchEvidence == null ? new ArrayList<>() : new ArrayList<>(matchEvidence);
    }

    public List<ReviewOperation> getOperations() {
        return operations;
    }

    public void setOperations(List<ReviewOperation> operations) {
        this.operations = operations == null ? new ArrayList<>() : new ArrayList<>(operations);
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
