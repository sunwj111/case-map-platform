package com.casemap.hierarchy.featuremap;

import java.util.ArrayList;
import java.util.List;

public class RiskView {

    private List<RuleItem> rules = new ArrayList<>();
    private List<DefectItem> defects = new ArrayList<>();
    private List<RiskTagItem> tags = new ArrayList<>();

    public List<RuleItem> getRules() {
        return rules;
    }

    public void setRules(List<RuleItem> rules) {
        this.rules = rules;
    }

    public List<DefectItem> getDefects() {
        return defects;
    }

    public void setDefects(List<DefectItem> defects) {
        this.defects = defects;
    }

    public List<RiskTagItem> getTags() {
        return tags;
    }

    public void setTags(List<RiskTagItem> tags) {
        this.tags = tags;
    }
}
