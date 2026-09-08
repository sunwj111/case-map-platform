package com.casemap.hierarchy.featuremap;

import com.casemap.hierarchy.asset.CaseAsset;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 功能点质量摘要（E-07 / I-01 地图侧口径）。
 * 只按正式用例统计，禁止按原型样例比例推算。
 */
@Service
public class QualitySummaryService {

    public static final String METRIC_SOURCE = "official-case-assets";
    public static final String DEFECT_SOURCE_UNAVAILABLE = "unavailable";

    public QualitySummary summarize(List<CaseAsset> cases, BusinessView businessView) {
        List<CaseAsset> officialCases = cases == null ? List.of() : cases;
        QualitySummary summary = new QualitySummary();
        summary.setLinkedCaseCount(officialCases.size());
        summary.setMetricSource(METRIC_SOURCE);
        summary.setDefectSource(DEFECT_SOURCE_UNAVAILABLE);
        summary.setDefectCount30d(0);

        Map<String, Object> distribution = new LinkedHashMap<>();
        int p0 = 0;
        int p1 = 0;
        int p2 = 0;
        int p3 = 0;
        for (CaseAsset asset : officialCases) {
            String priority = asset.getPriority() == null ? "" : asset.getPriority().toUpperCase(Locale.ROOT);
            if (priority.startsWith("P0")) {
                p0++;
            } else if (priority.startsWith("P1")) {
                p1++;
            } else if (priority.startsWith("P2")) {
                p2++;
            } else if (priority.startsWith("P3")) {
                p3++;
            }
        }
        int total = Math.max(officialCases.size(), 1);
        distribution.put("P0", p0);
        distribution.put("P1", p1);
        distribution.put("P2", p2);
        distribution.put("P3", p3);
        distribution.put("P0Rate", roundRate(p0, total));
        distribution.put("P1Rate", roundRate(p1, total));
        distribution.put("P2Rate", roundRate(p2, total));
        distribution.put("P3Rate", roundRate(p3, total));
        summary.setPriorityDistribution(distribution);

        long withScriptHint = officialCases.stream()
                .filter(asset -> asset.getApi() != null && !asset.getApi().isBlank())
                .count();
        summary.setAutomationCoverage(officialCases.isEmpty() ? 0.0 : roundRate((int) withScriptHint, officialCases.size()));

        int gapCount = 0;
        if (businessView != null && businessView.getScenarios() != null) {
            gapCount = (int) businessView.getScenarios().stream()
                    .filter(scenario -> "gap".equals(scenario.getCoverageStatus()))
                    .count();
        }
        summary.setGapCount(gapCount);
        if (officialCases.isEmpty()) {
            summary.setCoverageStatus("gap");
        } else if (gapCount > 0 || summary.getAutomationCoverage() < 0.7) {
            summary.setCoverageStatus("partial");
        } else {
            summary.setCoverageStatus("covered");
        }
        return summary;
    }

    static double roundRate(int count, int total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round((count * 1000.0) / total) / 1000.0;
    }
}
