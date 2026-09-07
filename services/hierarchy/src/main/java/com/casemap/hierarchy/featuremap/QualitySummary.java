package com.casemap.hierarchy.featuremap;

import java.util.LinkedHashMap;
import java.util.Map;

public class QualitySummary {

    private int linkedCaseCount;
    private Map<String, Object> priorityDistribution = new LinkedHashMap<>();
    private double automationCoverage;
    private int defectCount30d;
    private int gapCount;
    private String coverageStatus;

    public int getLinkedCaseCount() {
        return linkedCaseCount;
    }

    public void setLinkedCaseCount(int linkedCaseCount) {
        this.linkedCaseCount = linkedCaseCount;
    }

    public Map<String, Object> getPriorityDistribution() {
        return priorityDistribution;
    }

    public void setPriorityDistribution(Map<String, Object> priorityDistribution) {
        this.priorityDistribution = priorityDistribution;
    }

    public double getAutomationCoverage() {
        return automationCoverage;
    }

    public void setAutomationCoverage(double automationCoverage) {
        this.automationCoverage = automationCoverage;
    }

    public int getDefectCount30d() {
        return defectCount30d;
    }

    public void setDefectCount30d(int defectCount30d) {
        this.defectCount30d = defectCount30d;
    }

    public int getGapCount() {
        return gapCount;
    }

    public void setGapCount(int gapCount) {
        this.gapCount = gapCount;
    }

    public String getCoverageStatus() {
        return coverageStatus;
    }

    public void setCoverageStatus(String coverageStatus) {
        this.coverageStatus = coverageStatus;
    }
}
