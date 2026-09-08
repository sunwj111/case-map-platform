package com.casemap.hierarchy.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class QuoteKnowledgeFile {

    private String source;
    private List<QuoteRule> rules = new ArrayList<>();
    private List<QuoteField> fields = new ArrayList<>();

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<QuoteRule> getRules() {
        return rules;
    }

    public void setRules(List<QuoteRule> rules) {
        this.rules = rules;
    }

    public List<QuoteField> getFields() {
        return fields;
    }

    public void setFields(List<QuoteField> fields) {
        this.fields = fields;
    }
}
