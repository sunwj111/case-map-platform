package com.casemap.hierarchy.produce;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NaturalLanguageKnowledgeExtractor {

    private static final Set<String> STOP_WORDS = Set.of(
            "以及", "或者", "并且", "然后", "进行", "可以", "需要", "如果", "当", "的", "了", "是", "在",
            "我们", "系统", "用户", "测试", "用例", "知识库", "流程", "业务", "说明", "如下", "包括",
            "主要", "相关", "一般", "时候", "之后", "之前", "通过", "完成", "支持"
    );
    private static final Pattern SECTION_PATTERN = Pattern.compile("^#{1,3}\\s*(.+?)\\s*$", Pattern.MULTILINE);
    private static final Pattern SCENE_LEAD = Pattern.compile("^(?:业务)?场景[:：]\\s*(.+)$");
    private static final Pattern FEATURE_LEAD = Pattern.compile("^(?:功能点|功能|能力|模块)[:：]\\s*(.+)$");
    private static final Pattern RULE_LEAD = Pattern.compile("^(?:规则|约束|校验)[:：]\\s*(.+)$");
    private static final Pattern NODE_LEAD = Pattern.compile("^(?:流程节点|节点)[:：]\\s*(.+)$");
    private static final Pattern INCLUDE_PATTERN = Pattern.compile(
            "(?:包括|涵盖|链路(?:为|是)?|流程(?:为|是)?|环节(?:有|包括)?|主要(?:有|包括))[:：]?\\s*([^\\n。；;]{4,80})"
    );
    private static final Pattern FEATURE_PATTERN = Pattern.compile(
            "(?:支持|提供|完成|实现|负责|用于)\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{2,16})"
    );
    private static final Pattern RULE_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9]{2,20}(?:必须|不可|不能|应当|禁止)[^\\n。；;]{0,20})"
    );

    public KnowledgeExtractionResult extract(String text, String sourceVersion) {
        String sourceText = text == null ? "" : text.trim();
        if (sourceText.isEmpty()) {
            throw new IllegalArgumentException("知识文本不能为空");
        }

        CandidateBag scenes = new CandidateBag();
        CandidateBag features = new CandidateBag();
        CandidateBag rules = new CandidateBag();
        CandidateBag nodes = new CandidateBag();
        boolean hasSections = extractSections(sourceText, scenes, features, rules, nodes);
        extractFreeText(sourceText, scenes, features, rules, nodes);
        removeCrossCategoryDuplicates(scenes, features);

        return new KnowledgeExtractionResult(
                scenes.values(24),
                features.values(30),
                rules.values(24),
                nodes.values(24),
                hasSections ? "structured+nl" : "nl",
                sourceVersion
        );
    }

    private static boolean extractSections(
            String text,
            CandidateBag scenes,
            CandidateBag features,
            CandidateBag rules,
            CandidateBag nodes
    ) {
        Matcher matcher = SECTION_PATTERN.matcher(text);
        List<Section> sections = new ArrayList<>();
        while (matcher.find()) {
            sections.add(new Section(matcher.group(1).trim(), matcher.start(), matcher.end()));
        }
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            Section section = sections.get(sectionIndex);
            int bodyEnd = sectionIndex + 1 < sections.size()
                    ? sections.get(sectionIndex + 1).headingStart()
                    : text.length();
            String body = text.substring(section.bodyStart(), bodyEnd);
            List<String> items = Arrays.stream(body.split("\\R"))
                    .map(NaturalLanguageKnowledgeExtractor::cleanItem)
                    .filter(item -> !item.isEmpty())
                    .toList();

            String title = section.title();
            if (title.matches(".*(?:流程节点|节点).*")) {
                items.forEach(item -> nodes.add(item, "章节·节点", 90));
            } else if (title.matches(".*(?:场景|流程|环节|阶段).*")) {
                items.forEach(item -> scenes.add(item, "章节·场景", 90));
            } else if (title.matches(".*(?:功能|能力|模块).*")) {
                items.forEach(item -> features.add(item, "章节·功能点", 90));
            } else if (title.matches(".*(?:规则|状态|约束|校验|判定).*")) {
                items.forEach(item -> rules.add(item, "章节·规则", 90));
            } else {
                for (String item : items) {
                    if (isRule(item)) {
                        rules.add(item, "章节·其他", 75);
                    } else if (item.length() <= 14) {
                        features.add(item, "章节·其他", 70);
                    }
                }
            }
        }
        return !sections.isEmpty();
    }

    private static void extractFreeText(
            String text,
            CandidateBag scenes,
            CandidateBag features,
            CandidateBag rules,
            CandidateBag nodes
    ) {
        for (String line : text.split("\\R")) {
            String compactLine = FileParsingSupport.compact(line);
            if (compactLine.isEmpty() || compactLine.startsWith("#")) {
                continue;
            }
            if (addLeadCandidates(compactLine, NODE_LEAD, nodes, "行首·节点")) {
                continue;
            }
            if (addLeadCandidates(compactLine, SCENE_LEAD, scenes, "行首·场景")) {
                continue;
            }
            if (addLeadCandidates(compactLine, FEATURE_LEAD, features, "行首·功能点")) {
                continue;
            }
            if (addLeadCandidates(compactLine, RULE_LEAD, rules, "行首·规则")) {
                continue;
            }
            if (line.matches("^\\s*(?:[-*+•·]|\\d+[.、)])\\s*.*")) {
                String item = cleanItem(compactLine);
                if (item.isEmpty()) {
                    continue;
                }
                if (isRule(item)) {
                    rules.add(item, "列表·规则", 78);
                } else if (item.matches(".*(?:流程节点|节点).*") && item.length() <= 18) {
                    nodes.add(item, "列表·节点", 75);
                } else if (item.matches(".*(?:场景|流程|环节|阶段|审核|提交|生成|汇总|计算|触发).*")
                        && item.length() <= 18) {
                    scenes.add(item, "列表·场景", 75);
                } else if (item.length() <= 16) {
                    features.add(item, "列表·功能点", 72);
                }
            }
        }

        Matcher includeMatcher = INCLUDE_PATTERN.matcher(text);
        while (includeMatcher.find()) {
            for (String item : splitList(includeMatcher.group(1))) {
                if (isRule(item)) {
                    rules.add(item, "句式·规则", 72);
                } else if (item.length() <= 16) {
                    scenes.add(item, "句式·场景", 68);
                } else {
                    features.add(item, "句式·功能点", 68);
                }
            }
        }

        Matcher featureMatcher = FEATURE_PATTERN.matcher(text);
        while (featureMatcher.find()) {
            String feature = cleanItem(featureMatcher.group(1));
            if (!feature.matches(".*(?:系统|平台|用户|数据).*")) {
                features.add(feature, "句式·功能点", 70);
            }
        }

        Matcher ruleMatcher = RULE_PATTERN.matcher(text);
        while (ruleMatcher.find()) {
            rules.add(ruleMatcher.group(1), "句式·规则", 75);
        }
    }

    private static boolean addLeadCandidates(
            String line,
            Pattern pattern,
            CandidateBag candidateBag,
            String source
    ) {
        Matcher matcher = pattern.matcher(line);
        if (!matcher.matches()) {
            return false;
        }
        for (String item : splitList(matcher.group(1))) {
            candidateBag.add(item, source, 85);
        }
        return true;
    }

    private static List<String> splitList(String value) {
        return Arrays.stream(value.split("[、，,；;|/]|(?:和(?!同))|(?:与(?!外))"))
                .map(NaturalLanguageKnowledgeExtractor::cleanItem)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static String cleanItem(String rawValue) {
        String value = FileParsingSupport.compact(rawValue)
                .replaceFirst("^(?:包括|涵盖|主要有|主要是)[:：]?\\s*", "")
                .replaceFirst("^[-*+•·\\d.、)\\]】\\s]+", "")
                .replaceFirst("^[（(]?\\d+[）).、]\\s*", "")
                .replaceFirst("^[Rrｒ]\\d+[\\s:：-]*", "")
                .replaceFirst("[:：]\\s*$", "")
                .replaceFirst("[。；;]+$", "");
        if (value.length() < 2 || value.length() > 40 || STOP_WORDS.contains(value)) {
            return "";
        }
        if (value.matches("^(?:注|说明|例如|比如).*")) {
            return "";
        }
        return value;
    }

    private static boolean isRule(String value) {
        return value.matches(".*(?:必须|不可|不能|应当|禁止|校验|拦截|为空|非法).*");
    }

    private static void removeCrossCategoryDuplicates(CandidateBag scenes, CandidateBag features) {
        for (String scene : scenes.texts()) {
            if (scene.matches(".*(?:场景|流程|环节|阶段|审核|提交).*")) {
                features.remove(scene);
            }
        }
    }

    private record Section(String title, int headingStart, int bodyStart) {
    }

    private static final class CandidateBag {
        private final Map<String, KeywordCandidate> candidates = new LinkedHashMap<>();

        void add(String rawText, String source, int confidence) {
            String text = cleanItem(rawText);
            if (text.isEmpty()) {
                return;
            }
            KeywordCandidate existing = candidates.get(text);
            if (existing == null || confidence > existing.confidence()) {
                candidates.put(text, new KeywordCandidate(text, source, confidence));
            }
        }

        void remove(String text) {
            candidates.remove(text);
        }

        Set<String> texts() {
            return Set.copyOf(candidates.keySet());
        }

        List<KeywordCandidate> values(int limit) {
            List<KeywordCandidate> values = new ArrayList<>(candidates.values());
            values.removeIf(candidate -> values.stream().anyMatch(other ->
                    !other.text().equals(candidate.text())
                            && other.text().contains(candidate.text())
                            && other.text().length() >= candidate.text().length() + 2
            ));
            return values.stream().limit(limit).toList();
        }
    }
}
