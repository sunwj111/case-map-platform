package com.casemap.hierarchy.produce;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CaseCleaningService {

    public MapDraftResult generate(ImportBatch batch) {
        if (batch.getCaseImport() == null || batch.getCaseImport().rows().isEmpty()) {
            throw new IllegalArgumentException("请先导入历史用例");
        }
        if (batch.getKeywordExtraction() == null) {
            throw new IllegalArgumentException("请先抽取并确认知识库关键字");
        }
        if (!batch.isKeywordsConfirmed()) {
            throw new IllegalArgumentException("请先确认关键字");
        }

        KnowledgeExtractionResult keywords = batch.getKeywordExtraction();
        List<CleanedCaseItem> cleanedCases = new ArrayList<>();
        for (ParsedCaseRow caseRow : batch.getCaseImport().rows()) {
            cleanedCases.add(cleanCase(caseRow, keywords));
        }
        List<GapCaseCandidate> gaps = buildGaps(cleanedCases, keywords);
        MapDraftStats stats = buildStats(cleanedCases, gaps);
        return new MapDraftResult(
                batch.getId(),
                List.copyOf(cleanedCases),
                gaps,
                stats,
                Instant.now().toString()
        );
    }

    private static CleanedCaseItem cleanCase(
            ParsedCaseRow caseRow,
            KnowledgeExtractionResult keywords
    ) {
        String searchableText = normalize(String.join(
                " ",
                caseRow.caseName(),
                caseRow.step(),
                caseRow.expected(),
                caseRow.scene(),
                caseRow.feature(),
                caseRow.module()
        ));
        KeywordCandidate matchedScene = bestMatch(searchableText, keywords.scenes());
        KeywordCandidate matchedFeature = bestMatch(searchableText, keywords.features());
        KeywordCandidate matchedNode = bestMatch(searchableText, keywords.nodes());

        String scene = firstNonBlank(
                caseRow.scene(),
                matchedScene == null ? null : matchedScene.text(),
                "未识别场景"
        );
        String feature = firstNonBlank(
                caseRow.feature(),
                matchedFeature == null ? null : matchedFeature.text(),
                "未识别功能点"
        );
        String flowNode = matchedNode == null ? "" : matchedNode.text();

        List<String> evidence = new ArrayList<>();
        int confidence = 40;
        if (!caseRow.scene().isBlank()) {
            confidence += 15;
            evidence.add("历史用例已有场景");
        }
        if (!caseRow.feature().isBlank()) {
            confidence += 20;
            evidence.add("历史用例已有功能点");
        }
        if (!caseRow.originalCaseId().startsWith("IMPORT-ROW-")
                && !caseRow.originalCaseId().startsWith("XMIND-ROW-")) {
            confidence += 5;
            evidence.add("保留原始用例ID");
        }
        if (matchedScene != null) {
            confidence += 5;
            evidence.add("命中知识场景：" + matchedScene.text());
        }
        if (matchedFeature != null) {
            confidence += 10;
            evidence.add("命中知识功能点：" + matchedFeature.text());
        }
        if (matchedNode != null) {
            confidence += 5;
            evidence.add("命中流程节点：" + matchedNode.text());
        }
        confidence = Math.min(confidence, 95);

        return new CleanedCaseItem(
                caseRow.originalCaseId(),
                caseRow.caseName(),
                scene,
                feature,
                flowNode,
                confidence,
                mountAdvice(confidence),
                List.copyOf(evidence),
                caseRow.sourceRowNumber()
        );
    }

    private static KeywordCandidate bestMatch(String searchableText, List<KeywordCandidate> candidates) {
        KeywordCandidate bestCandidate = null;
        for (KeywordCandidate candidate : candidates) {
            String normalizedCandidate = normalize(candidate.text());
            if (normalizedCandidate.length() < 2 || !searchableText.contains(normalizedCandidate)) {
                continue;
            }
            if (bestCandidate == null || candidate.text().length() > bestCandidate.text().length()) {
                bestCandidate = candidate;
            }
        }
        return bestCandidate;
    }

    private static List<GapCaseCandidate> buildGaps(
            List<CleanedCaseItem> cleanedCases,
            KnowledgeExtractionResult keywords
    ) {
        Set<String> coveredFeatures = new LinkedHashSet<>();
        for (CleanedCaseItem cleanedCase : cleanedCases) {
            coveredFeatures.add(normalize(cleanedCase.feature()));
        }
        String defaultScene = keywords.scenes().isEmpty()
                ? "待确认场景"
                : keywords.scenes().get(0).text();
        List<GapCaseCandidate> gaps = new ArrayList<>();
        for (KeywordCandidate feature : keywords.features()) {
            if (!isBusinessGapCandidate(feature)) {
                continue;
            }
            String normalizedFeature = normalize(feature.text());
            boolean covered = coveredFeatures.stream().anyMatch(existingFeature ->
                    existingFeature.equals(normalizedFeature)
                            || existingFeature.contains(normalizedFeature)
                            || normalizedFeature.contains(existingFeature)
            );
            if (!covered) {
                gaps.add(new GapCaseCandidate(
                        "GAP-" + String.format("%03d", gaps.size() + 1),
                        defaultScene,
                        feature.text(),
                        "知识库功能点未匹配历史用例"
                ));
            }
            if (gaps.size() >= 50) {
                break;
            }
        }
        return List.copyOf(gaps);
    }

    private static boolean isBusinessGapCandidate(KeywordCandidate feature) {
        String source = feature.source();
        return !feature.text().startsWith("/")
                && (source.contains("章节标题·功能点")
                || source.contains("章节·功能点")
                || source.contains("行首·功能点")
                || source.equals("人工确认"));
    }

    private static MapDraftStats buildStats(
            List<CleanedCaseItem> cleanedCases,
            List<GapCaseCandidate> gaps
    ) {
        int autoMounted = 0;
        int reviewRequired = 0;
        int manualRequired = 0;
        for (CleanedCaseItem cleanedCase : cleanedCases) {
            if (cleanedCase.confidence() >= 85) {
                autoMounted++;
            } else if (cleanedCase.confidence() >= 70) {
                reviewRequired++;
            } else {
                manualRequired++;
            }
        }
        return new MapDraftStats(
                cleanedCases.size(),
                autoMounted,
                reviewRequired,
                manualRequired,
                gaps.size()
        );
    }

    private static String mountAdvice(int confidence) {
        if (confidence >= 85) {
            return "自动挂载";
        }
        if (confidence >= 70) {
            return "待抽检";
        }
        return "人工确认";
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return fallback;
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[\\s`*_:/()（）\\-]", "");
    }
}
