package com.casemap.hierarchy.featuremap;

import com.casemap.hierarchy.asset.CaseAsset;
import com.casemap.hierarchy.featurekey.FeatureKey;
import com.casemap.hierarchy.knowledge.QuoteKnowledgeCatalog;
import com.casemap.hierarchy.knowledge.QuoteRule;
import com.casemap.hierarchy.tech.ResolvedFlowNode;
import com.casemap.hierarchy.tech.TechMappingResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则匹配与风险标签（E-07 / I-02 地图侧）。
 */
@Service
public class RuleRiskService {

    private final QuoteKnowledgeCatalog knowledgeCatalog;

    public RuleRiskService(QuoteKnowledgeCatalog knowledgeCatalog) {
        this.knowledgeCatalog = knowledgeCatalog;
    }

    public RiskView build(
            FeatureKey parts,
            List<CaseAsset> cases,
            TechMappingResult tech,
            BusinessSpine spine,
            QualitySummary summary
    ) {
        RiskView view = new RiskView();
        List<String> flowNodeNames = flowNodeNames(tech);
        List<QuoteRule> matched = knowledgeCatalog.findRules(parts.scene(), parts.feature(), flowNodeNames);
        List<RuleItem> rules = new ArrayList<>();
        for (QuoteRule rule : matched) {
            RuleItem item = new RuleItem();
            item.setId(rule.getId());
            item.setName(rule.getName());
            item.setSource(knowledgeCatalog.getSource());
            rules.add(item);
        }
        view.setRules(rules);
        view.setDefects(List.of());
        view.setTags(buildTags(parts, cases, tech, spine, summary, rules.isEmpty()));
        return view;
    }

    private List<RiskTagItem> buildTags(
            FeatureKey parts,
            List<CaseAsset> cases,
            TechMappingResult tech,
            BusinessSpine spine,
            QualitySummary summary,
            boolean rulesMissing
    ) {
        List<RiskTagItem> tags = new ArrayList<>();
        boolean highRisk = tech != null && tech.getFlowNodes().stream()
                .anyMatch(node -> node.getRisk() != null && node.getRisk().contains("高"));
        if (highRisk) {
            tags.add(tag("risk", "高风险", "high"));
        }
        if (containsAny(parts.feature(), "价", "金额") || containsAny(parts.scene(), "金额")) {
            tags.add(tag("calc", "金额计算", "high"));
        }
        if (containsAny(parts.feature(), "审核") || containsAny(parts.scene(), "审核")) {
            tags.add(tag("audit", "审核", "high"));
        }
        if (spine != null && spine.getValueTags().contains("核心链路")) {
            tags.add(tag("chain", "核心链路", "high"));
        }
        if (cases == null || cases.isEmpty()) {
            tags.add(tag("gap", "无正式用例", "medium"));
        }
        if (summary != null && summary.getGapCount() > 0) {
            tags.add(tag("gap", "有缺口", "medium"));
        }
        if (summary != null && summary.getLinkedCaseCount() > 0 && summary.getAutomationCoverage() < 0.7) {
            tags.add(tag("auto", "自动化不足", "medium"));
        }
        if (rulesMissing) {
            tags.add(tag("rule", "规则待校准", "medium"));
        }
        return tags;
    }

    private static List<String> flowNodeNames(TechMappingResult tech) {
        if (tech == null || tech.getFlowNodes() == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (ResolvedFlowNode node : tech.getFlowNodes()) {
            if (node.getName() != null && !node.getName().isBlank()) {
                names.add(node.getName());
            }
        }
        return names;
    }

    private static boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static RiskTagItem tag(String type, String label, String level) {
        RiskTagItem item = new RiskTagItem();
        item.setType(type);
        item.setLabel(label);
        item.setLevel(level);
        return item;
    }
}
