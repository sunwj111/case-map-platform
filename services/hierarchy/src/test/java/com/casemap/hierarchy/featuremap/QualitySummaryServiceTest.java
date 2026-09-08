package com.casemap.hierarchy.featuremap;

import com.casemap.hierarchy.asset.CaseAsset;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QualitySummaryServiceTest {

    @Test
    void countsOfficialCasesWithoutSampleRatios() {
        CaseAsset p0Case = new CaseAsset();
        p0Case.setPriority("P0");
        p0Case.setApi("POST /quotation/offer");
        CaseAsset p1Case = new CaseAsset();
        p1Case.setPriority("P1");

        ScenarioItem covered = new ScenarioItem();
        covered.setCoverageStatus("covered");
        ScenarioItem gap = new ScenarioItem();
        gap.setCoverageStatus("gap");
        BusinessView businessView = new BusinessView();
        businessView.setScenarios(List.of(covered, gap));

        QualitySummary summary = new QualitySummaryService().summarize(List.of(p0Case, p1Case), businessView);

        assertEquals(2, summary.getLinkedCaseCount());
        assertEquals(1, summary.getPriorityDistribution().get("P0"));
        assertEquals(1, summary.getPriorityDistribution().get("P1"));
        assertEquals(0.5, summary.getAutomationCoverage());
        assertEquals(1, summary.getGapCount());
        assertEquals("partial", summary.getCoverageStatus());
        assertEquals("official-case-assets", summary.getMetricSource());
        assertEquals("unavailable", summary.getDefectSource());
        assertEquals(0, summary.getDefectCount30d());
    }
}
