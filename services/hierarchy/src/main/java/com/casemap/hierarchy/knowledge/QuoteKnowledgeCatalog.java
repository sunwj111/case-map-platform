package com.casemap.hierarchy.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Repository
public class QuoteKnowledgeCatalog {

    private final QuoteKnowledgeFile file;

    public QuoteKnowledgeCatalog(ObjectMapper objectMapper) throws IOException {
        try (InputStream input = requiredResource("/data/quote_knowledge.json")) {
            this.file = objectMapper.readValue(input, QuoteKnowledgeFile.class);
        }
    }

    public String getSource() {
        return file.getSource() == null || file.getSource().isBlank()
                ? "configs/quote.json"
                : file.getSource();
    }

    public List<QuoteRule> listRules() {
        return Collections.unmodifiableList(file.getRules());
    }

    public List<QuoteRule> findRules(String scene, String feature, List<String> flowNodeNames) {
        List<QuoteRule> nodeMatched = new ArrayList<>();
        for (QuoteRule rule : file.getRules()) {
            if (matchesNode(rule.getNode(), scene, flowNodeNames)) {
                nodeMatched.add(rule);
            }
        }
        List<QuoteRule> featureMatched = new ArrayList<>();
        for (QuoteRule rule : nodeMatched) {
            if (matchesFeature(rule, feature)) {
                featureMatched.add(rule);
            }
        }
        if (featureMatched.isEmpty()) {
            return nodeMatched;
        }
        for (QuoteRule rule : nodeMatched) {
            if (featureMatched.contains(rule)) {
                continue;
            }
            if ("边界值".equals(rule.getTestType())) {
                featureMatched.add(rule);
            }
        }
        return featureMatched;
    }

    public List<String> findTables(String scene, List<String> flowNodeNames) {
        Set<String> tables = new LinkedHashSet<>();
        for (QuoteField field : file.getFields()) {
            if (!matchesNode(field.getNode(), scene, flowNodeNames)) {
                continue;
            }
            String tableName = toTableName(field.getEntity());
            if (tableName != null) {
                tables.add(tableName);
            }
        }
        return new ArrayList<>(tables);
    }

    static boolean matchesNode(String nodeName, String scene, List<String> flowNodeNames) {
        if (nodeName == null || nodeName.isBlank()) {
            return false;
        }
        if (scene != null && (nodeName.equals(scene) || scene.contains(nodeName) || nodeName.contains(scene))) {
            return true;
        }
        return flowNodeNames != null && flowNodeNames.contains(nodeName);
    }

    static boolean matchesFeature(QuoteRule rule, String feature) {
        if (feature == null || feature.isBlank()) {
            return false;
        }
        String ruleName = rule.getName() == null ? "" : rule.getName().toLowerCase(Locale.ROOT);
        String haystack = (ruleName + " " + (rule.getDescription() == null ? "" : rule.getDescription()))
                .toLowerCase(Locale.ROOT);
        String needle = feature.toLowerCase(Locale.ROOT);
        if (haystack.contains(needle) || (!ruleName.isEmpty() && needle.contains(ruleName))) {
            return true;
        }
        for (String token : featureTokens(feature)) {
            if (haystack.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    static List<String> featureTokens(String feature) {
        List<String> tokens = new ArrayList<>();
        String remaining = feature;
        for (String splitter : List.of("与", "和", "及")) {
            remaining = remaining.replace(splitter, " ");
        }
        for (String part : remaining.split("\\s+")) {
            if (part.length() >= 2) {
                tokens.add(part);
            }
        }
        if (feature.contains("数量价")) {
            tokens.add("数量价");
        }
        if (feature.contains("固定价")) {
            tokens.add("固定价");
        }
        if (feature.contains("去利润")) {
            tokens.add("去利润");
        }
        if (feature.contains("分组")) {
            tokens.add("分组");
        }
        if (feature.contains("审核")) {
            tokens.add("审核");
        }
        if (feature.contains("必填") || feature.contains("参数")) {
            tokens.add("参数");
            tokens.add("必填");
        }
        return tokens;
    }

    static String toTableName(String entity) {
        if (entity == null || entity.isBlank()) {
            return null;
        }
        StringBuilder tableName = new StringBuilder();
        for (int index = 0; index < entity.length(); index++) {
            char character = entity.charAt(index);
            if (Character.isUpperCase(character) && tableName.length() > 0) {
                tableName.append('_');
            }
            tableName.append(Character.toLowerCase(character));
        }
        return tableName.toString();
    }

    private static InputStream requiredResource(String path) {
        InputStream input = QuoteKnowledgeCatalog.class.getResourceAsStream(path);
        if (input == null) {
            throw new IllegalStateException("缺少资源文件：" + path);
        }
        return input;
    }
}
