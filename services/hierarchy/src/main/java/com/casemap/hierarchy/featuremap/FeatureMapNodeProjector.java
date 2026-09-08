package com.casemap.hierarchy.featuremap;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 FeatureMapDTO 投影为统一 MapNode 列表，供列组件 / 图谱消费（M1-S03）。
 */
public final class FeatureMapNodeProjector {

    private FeatureMapNodeProjector() {
    }

    public static List<MapNode> project(FeatureMapDto map) {
        List<MapNode> nodes = new ArrayList<>();
        if (map == null || map.getMeta() == null) {
            return nodes;
        }

        String featureKey = map.getMeta().getFeatureKey();
        BusinessSpine spine = map.getSpine();
        MapNode featureNode = new MapNode("feature:" + featureKey, MapNodeType.feature,
                spine != null ? spine.getFeatureName() : featureKey);
        featureNode.setStatus(MapNodeStatus.ready);
        featureNode.setPayload(spine);
        nodes.add(featureNode);

        if (spine != null) {
            for (NeighborFeature neighbor : spine.getNeighborFeatures()) {
                MapNode node = new MapNode("neighbor:" + neighbor.getFeatureKey(), MapNodeType.neighbor,
                        neighbor.getFeatureName());
                node.setPayload(neighbor);
                nodes.add(node);
            }
        }

        BusinessView business = map.getBusinessView();
        if (business != null) {
            for (ScenarioItem scenario : business.getScenarios()) {
                MapNode node = new MapNode("scene:" + scenario.getId(), MapNodeType.scene, scenario.getName());
                node.setStatus(coverageToStatus(scenario.getCoverageStatus()));
                node.setPayload(scenario);
                nodes.add(node);
            }
            for (CaseItem caseItem : business.getCases()) {
                MapNode node = new MapNode("case:" + caseItem.getId(), MapNodeType.case_node, caseItem.getName());
                node.setConfidence(caseItem.getConfidence());
                node.setStatus(confidenceToStatus(caseItem.getConfidence()));
                node.setPayload(caseItem);
                nodes.add(node);
            }
            for (ScriptItem script : business.getScripts()) {
                MapNode node = new MapNode("script:" + script.getId(), MapNodeType.script, script.getName());
                node.setPayload(script);
                nodes.add(node);
            }
            for (DataTemplateItem template : business.getDataTemplates()) {
                MapNode node = new MapNode("data:" + template.getId(), MapNodeType.data, template.getName());
                node.setStatus("unavailable".equals(template.getSource()) ? MapNodeStatus.missing : MapNodeStatus.ready);
                node.setPayload(template);
                nodes.add(node);
            }
            for (ExecutionItem execution : business.getExecutions()) {
                MapNode node = new MapNode("execution:" + execution.getId(), MapNodeType.execution, execution.getLabel());
                node.setStatus("unavailable".equals(execution.getSource()) ? MapNodeStatus.missing : MapNodeStatus.ready);
                node.setPayload(execution);
                nodes.add(node);
            }
        }

        TechView tech = map.getTechView();
        if (tech != null) {
            int apiIndex = 0;
            for (ApiItem api : tech.getApis()) {
                String title = api.getLabel() != null ? api.getLabel() : api.getMethod() + " " + api.getPath();
                MapNode node = new MapNode("api:" + apiIndex + "_" + api.getPath(), MapNodeType.api, title);
                node.setConfidence(api.getConfidence());
                node.setStatus(confidenceToStatus(api.getConfidence()));
                node.setPayload(api);
                nodes.add(node);
                apiIndex++;
            }
            for (ServiceItem service : tech.getServices()) {
                MapNode node = new MapNode("service:" + service.getName(), MapNodeType.service, service.getName());
                node.setPayload(service);
                nodes.add(node);
            }
            for (CodeModuleItem module : tech.getCodeModules()) {
                MapNode node = new MapNode("module:" + module.getName(), MapNodeType.module, module.getName());
                node.setPayload(module);
                nodes.add(node);
            }
        }

        RiskView risk = map.getRiskView();
        if (risk != null) {
            for (RuleItem rule : risk.getRules()) {
                MapNode node = new MapNode("rule:" + rule.getId(), MapNodeType.rule, rule.getName());
                node.setPayload(rule);
                nodes.add(node);
            }
            for (DefectItem defect : risk.getDefects()) {
                MapNode node = new MapNode("defect:" + defect.getId(), MapNodeType.defect, defect.getTitle());
                node.setPayload(defect);
                nodes.add(node);
            }
        }

        return nodes;
    }

    private static MapNodeStatus coverageToStatus(String coverageStatus) {
        if ("gap".equalsIgnoreCase(coverageStatus)) {
            return MapNodeStatus.missing;
        }
        if ("partial".equalsIgnoreCase(coverageStatus)) {
            return MapNodeStatus.review;
        }
        return MapNodeStatus.ready;
    }

    private static MapNodeStatus confidenceToStatus(Integer confidence) {
        if (confidence == null) {
            return MapNodeStatus.ready;
        }
        if (confidence < 70) {
            return MapNodeStatus.review;
        }
        return MapNodeStatus.ready;
    }
}
