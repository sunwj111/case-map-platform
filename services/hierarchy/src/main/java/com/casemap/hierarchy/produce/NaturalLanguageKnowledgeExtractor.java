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
    private static final Pattern SECTION_PATTERN = Pattern.compile("^(#{1,3})\\s*(.+?)\\s*$", Pattern.MULTILINE);
    private static final Pattern SCENE_LEAD = Pattern.compile("^(?:业务)?场景[:：]\\s*(.+)$");
    private static final Pattern FEATURE_LEAD = Pattern.compile("^(?:功能点|功能|能力|模块)[:：]\\s*(.+)$");
    private static final Pattern RULE_LEAD = Pattern.compile("^(?:规则|约束|校验)[:：]\\s*(.+)$");
    private static final Pattern NODE_LEAD = Pattern.compile("^(?:流程节点|节点)[:：]\\s*(.+)$");
    private static final Pattern FEATURE_PATTERN = Pattern.compile(
            "(?:支持|提供|完成|实现|负责|用于)\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{2,16})"
    );
    private static final Pattern RULE_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9]{2,20}(?:必须|不可|不能|应当|禁止)[^\\n。；;]{0,20})"
    );
    private static final Pattern TECHNICAL_NODE_PATTERN = Pattern.compile(
            "\\b([A-Z][A-Za-z0-9]*(?:ServiceImpl|Service|Controller|Api|Dao|Adaptor|Client|Manager))\\b"
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
        extractMarkdownTables(sourceText, features, rules);
        extractTechnicalNodes(sourceText, nodes);
        removeCrossCategoryDuplicates(scenes, features);
        if (scenes.isEmpty() && features.isEmpty() && rules.isEmpty() && nodes.isEmpty()) {
            String shortKnowledge = cleanItem(sourceText);
            if (!shortKnowledge.isEmpty()) {
                features.add(shortKnowledge, "短文本·功能点", 65);
            }
        }
        if (scenes.isEmpty() && features.isEmpty() && rules.isEmpty() && nodes.isEmpty()) {
            throw new IllegalArgumentException("未从知识文本中抽取到有效候选，请上传知识文件或补充业务描述");
        }

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
            sections.add(new Section(
                    matcher.group(2).trim(),
                    matcher.group(1).length(),
                    matcher.start(),
                    matcher.end()
            ));
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
            String normalizedTitle = cleanItem(title);
            if (!normalizedTitle.isEmpty()
                    && normalizedTitle.matches(".*(?:流程|场景|审核|提交|计算|状态机).*")) {
                scenes.add(normalizedTitle, "章节标题·场景", 82);
            }
            if (section.level() >= 3
                    && !normalizedTitle.isEmpty()
                    && !normalizedTitle.matches(".*(?:接口清单|RPC 接口|涉及接口|调用链).*")) {
                features.add(normalizedTitle, "章节标题·功能点", 86);
            }
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
            if (compactLine.isEmpty()
                    || compactLine.startsWith("#")
                    || compactLine.startsWith("|")
                    || compactLine.startsWith("```")
                    || compactLine.matches("^[-:|\\s]+$")) {
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

    private static void extractMarkdownTables(String text, CandidateBag features, CandidateBag rules) {
        for (String line : text.split("\\R")) {
            String compactLine = line.trim();
            if (!compactLine.startsWith("|") || compactLine.matches("^\\|?[\\s:|-]+\\|?$")) {
                continue;
            }
            List<String> cells = Arrays.stream(compactLine.split("\\|"))
                    .map(NaturalLanguageKnowledgeExtractor::cleanItem)
                    .filter(cell -> !cell.isEmpty())
                    .toList();
            if (cells.isEmpty() || cells.stream().allMatch(cell ->
                    cell.matches(".*(?:接口|方法|说明|系统|调用类|方式|作用|校验|外部调用|拦截条件).*"))) {
                continue;
            }
            String firstCell = cells.get(0).replace("`", "");
            if (firstCell.startsWith("/")) {
                features.add(firstCell, "表格·功能点", 82);
            }
            String joinedCells = String.join("：", cells);
            if (isRule(joinedCells)) {
                rules.add(joinedCells, "表格·规则", 80);
            }
        }
    }

    private static void extractTechnicalNodes(String text, CandidateBag nodes) {
        Matcher nodeMatcher = TECHNICAL_NODE_PATTERN.matcher(text);
        while (nodeMatcher.find()) {
            nodes.add(nodeMatcher.group(1), "调用链·节点", 88);
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
        String compactValue = FileParsingSupport.compact(rawValue);
        if (compactValue.startsWith("```") || compactValue.matches(".*[│▼].*")) {
            return "";
        }
        String value = compactValue
                .replace("**", "")
                .replace("`", "")
                .replaceFirst("^(?:包括|涵盖|主要有|主要是)[:：]?\\s*", "")
                .replaceFirst("^[一二三四五六七八九十]+[、.．]\\s*", "")
                .replaceFirst("^[-*+•·\\d.、)\\]】\\s]+", "")
                .replaceFirst("^[（(]?\\d+[）).、]\\s*", "")
                .replaceFirst("^[Rrｒ]\\d+[\\s:：-]*", "")
                .replaceFirst("[:：]\\s*$", "")
                .replaceFirst("[。；;]+$", "");
        if (value.length() < 2
                || value.length() > 40
                || value.startsWith("|")
                || value.matches("^[-:|\\s]+$")
                || STOP_WORDS.contains(value)) {
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
            KeywordCandidate sceneCandidate = scenes.get(scene);
            KeywordCandidate featureCandidate = features.get(scene);
            if (featureCandidate != null
                    && scene.matches(".*(?:场景|流程|环节|阶段|审核|提交).*")
                    && sceneCandidate.confidence() >= featureCandidate.confidence()) {
                features.remove(scene);
            }
        }
    }

    private record Section(String title, int level, int headingStart, int bodyStart) {
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

        KeywordCandidate get(String text) {
            return candidates.get(text);
        }

        Set<String> texts() {
            return Set.copyOf(candidates.keySet());
        }

        boolean isEmpty() {
            return candidates.isEmpty();
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
