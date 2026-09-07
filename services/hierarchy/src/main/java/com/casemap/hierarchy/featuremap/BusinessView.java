package com.casemap.hierarchy.featuremap;

import java.util.ArrayList;
import java.util.List;

public class BusinessView {

    private List<ScenarioItem> scenarios = new ArrayList<>();
    private List<CaseItem> cases = new ArrayList<>();
    private List<ScriptItem> scripts = new ArrayList<>();
    private List<DataTemplateItem> dataTemplates = new ArrayList<>();
    private List<ExecutionItem> executions = new ArrayList<>();

    public List<ScenarioItem> getScenarios() {
        return scenarios;
    }

    public void setScenarios(List<ScenarioItem> scenarios) {
        this.scenarios = scenarios;
    }

    public List<CaseItem> getCases() {
        return cases;
    }

    public void setCases(List<CaseItem> cases) {
        this.cases = cases;
    }

    public List<ScriptItem> getScripts() {
        return scripts;
    }

    public void setScripts(List<ScriptItem> scripts) {
        this.scripts = scripts;
    }

    public List<DataTemplateItem> getDataTemplates() {
        return dataTemplates;
    }

    public void setDataTemplates(List<DataTemplateItem> dataTemplates) {
        this.dataTemplates = dataTemplates;
    }

    public List<ExecutionItem> getExecutions() {
        return executions;
    }

    public void setExecutions(List<ExecutionItem> executions) {
        this.executions = executions;
    }
}
