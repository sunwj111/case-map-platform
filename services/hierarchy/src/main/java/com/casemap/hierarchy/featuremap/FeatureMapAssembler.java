package com.casemap.hierarchy.featuremap;

import com.casemap.hierarchy.asset.CaseAsset;
import com.casemap.hierarchy.asset.CaseQueryService;
import com.casemap.hierarchy.featurekey.FeatureKey;
import com.casemap.hierarchy.knowledge.QuoteKnowledgeCatalog;
import com.casemap.hierarchy.model.HierarchyNode;
import com.casemap.hierarchy.model.NodeLevel;
import com.casemap.hierarchy.model.NodeStatus;
import com.casemap.hierarchy.store.HierarchyStore;
import com.casemap.hierarchy.tech.ApiResolveService;
import com.casemap.hierarchy.tech.ResolvedApi;
import com.casemap.hierarchy.tech.ResolvedFlowNode;
import com.casemap.hierarchy.tech.TechMappingResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 功能点地图组装主流程（E-04 / E-06 / E-07 / E-09）。
 * 多数据源聚合；任一源缺失时降级填充，不抛出 500。
 */
@Service
public class FeatureMapAssembler {

    private static final String MAP_VERSION = "1.0.0";
    private static final String DEFAULT_BASELINE = "BL-DRAFT";
    private static final String SYNTHETIC_VERSION = "1.0.0-synthetic";
    private static final String SOURCE_OFFICIAL_CASES = "official-case-assets";
    private static final String SOURCE_DERIVED_CASE = "derived-from-official-case";
    private static final String SOURCE_DERIVED_TECH = "derived-from-tech-mapping";
    private static final String SOURCE_DERIVED_SCENE = "derived-from-scene";
    private static final String SOURCE_UNAVAILABLE = "unavailable";

    private final HierarchyStore hierarchyStore;
    private final CaseQueryService caseQueryService;
    private final ApiResolveService apiResolveService;
    private final QuoteKnowledgeCatalog knowledgeCatalog;
    private final QualitySummaryService qualitySummaryService;
    private final RuleRiskService ruleRiskService;

    public FeatureMapAssembler(
            HierarchyStore hierarchyStore,
            CaseQueryService caseQueryService,
            ApiResolveService apiResolveService,
            QuoteKnowledgeCatalog knowledgeCatalog,
            QualitySummaryService qualitySummaryService,
            RuleRiskService ruleRiskService
    ) {
        this.hierarchyStore = hierarchyStore;
        this.caseQueryService = caseQueryService;
        this.apiResolveService = apiResolveService;
        this.knowledgeCatalog = knowledgeCatalog;
        this.qualitySummaryService = qualitySummaryService;
        this.ruleRiskService = ruleRiskService;
    }

    public FeatureMapDto assemble(String featureKeyInput) {
        FeatureKey parts = FeatureKey.parse(featureKeyInput);
        String featureKey = parts.toKey();

        List<String> dataSources = new ArrayList<>();
        dataSources.add("hierarchy");

        Optional<HierarchyNode> featureNode = safeFeatureNode(featureKey, dataSources);
        List<CaseAsset> cases = safeCases(featureKey, dataSources);
        TechMappingResult tech = safeTech(featureKey, parts, dataSources);

        FeatureMapDto map = new FeatureMapDto();
        map.setSpine(buildSpine(parts, featureNode.orElse(null), dataSources));
        map.setBusinessView(buildBusinessView(parts, cases, tech, dataSources));
        map.setTechView(buildTechView(parts, tech, dataSources));
        QualitySummary summary = qualitySummaryService.summarize(cases, map.getBusinessView());
        dataSources.add(QualitySummaryService.METRIC_SOURCE);
        dataSources.add("defects:" + QualitySummaryService.DEFECT_SOURCE_UNAVAILABLE);
        map.setSummary(summary);
        map.setRiskView(ruleRiskService.build(parts, cases, tech, map.getSpine(), summary));
        if (map.getRiskView().getRules().isEmpty()) {
            dataSources.add("rules:unmatched");
        } else {
            dataSources.add(knowledgeCatalog.getSource());
        }
        map.setMeta(buildMeta(featureKey, featureNode.orElse(null), dataSources));
        map.setConsumers(defaultConsumers());
        return map;
    }

    private Optional<HierarchyNode> safeFeatureNode(String featureKey, List<String> dataSources) {
        try {
            Optional<HierarchyNode> node = hierarchyStore.getByFeatureKey(featureKey);
            if (node.isEmpty()) {
                dataSources.add("hierarchy:missing");
            }
            return node;
        } catch (RuntimeException ex) {
            dataSources.add("hierarchy:degraded");
            return Optional.empty();
        }
    }

    private List<CaseAsset> safeCases(String featureKey, List<String> dataSources) {
        try {
            CaseQueryService.CaseQueryResult result = caseQueryService.queryForMap(featureKey);
            dataSources.add(result.getSource() == null ? "正式资产库" : result.getSource());
            return result.getItems();
        } catch (RuntimeException ex) {
            dataSources.add("case-assets:degraded");
            return List.of();
        }
    }

    private TechMappingResult safeTech(String featureKey, FeatureKey parts, List<String> dataSources) {
        try {
            TechMappingResult result = apiResolveService.resolve(featureKey, parts.feature(), parts.scene());
            if (result.getSource() != null && !result.getSource().isBlank()) {
                dataSources.add(result.getSource());
            } else {
                dataSources.add("tech-mapping");
            }
            if (result.isFallback()) {
                dataSources.add("tech-mapping:fallback");
            }
            return result;
        } catch (RuntimeException ex) {
            dataSources.add("tech-mapping:degraded");
            TechMappingResult empty = new TechMappingResult();
            empty.setFeature(parts.feature());
            empty.setScene(parts.scene());
            empty.setFallback(true);
            empty.setSource("degraded");
            empty.setApis(List.of());
            empty.setFlowNodes(List.of());
            return empty;
        }
    }

    private FeatureMapMeta buildMeta(String featureKey, HierarchyNode featureNode, List<String> dataSources) {
        boolean synthetic = featureNode == null;
        FeatureMapMeta meta = new FeatureMapMeta();
        meta.setMapId(synthetic ? "fm_synth_" + slug(featureKey) : mapIdOf(featureKey));
        meta.setFeatureKey(featureKey);
        meta.setVersion(synthetic ? SYNTHETIC_VERSION : MAP_VERSION);
        meta.setBaseline(DEFAULT_BASELINE);
        meta.setSynthetic(synthetic);
        if (synthetic) {
            dataSources.add(0, "synthetic");
        }
        meta.setUpdatedAt(featureNode != null && featureNode.getUpdatedAt() != null
                ? featureNode.getUpdatedAt()
                : LocalDate.now().toString());
        meta.setQualityOwner(featureNode != null && featureNode.getMeta() != null
                ? stringMeta(featureNode.getMeta().get("qualityOwner"))
                : null);
        if (meta.getQualityOwner() == null || meta.getQualityOwner().isBlank()) {
            meta.setQualityOwner("未指定");
        }
        meta.setDataSources(new ArrayList<>(new LinkedHashSet<>(dataSources)));
        return meta;
    }

    private BusinessSpine buildSpine(FeatureKey parts, HierarchyNode featureNode, List<String> dataSources) {
        BusinessSpine spine = new BusinessSpine();
        spine.setDomain(parts.domain());
        spine.setApp(parts.system());
        spine.setSceneName(parts.scene());
        spine.setFeatureName(parts.feature());
        spine.setProcessName(parts.scene().contains("审核") || parts.feature().contains("审核")
                ? "审核流程"
                : "报价流程");
        spine.setValueTags(buildValueTags(parts, featureNode));
        spine.setNeighborFeatures(buildNeighbors(parts, featureNode, dataSources));
        return spine;
    }

    private List<String> buildValueTags(FeatureKey parts, HierarchyNode featureNode) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (featureNode != null && featureNode.getMeta() != null) {
            Object raw = featureNode.getMeta().get("valueTags");
            if (raw instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null && !String.valueOf(item).isBlank()) {
                        tags.add(String.valueOf(item));
                    }
                }
            }
        }
        if (parts.scene().contains("金额") || parts.feature().contains("价")) {
            tags.add("金额计算");
        }
        if (parts.scene().contains("审核") || parts.feature().contains("审核")) {
            tags.add("审核");
        }
        if (parts.scene().contains("造价审核") || parts.feature().contains("造价审核")
                || parts.scene().equals("造价审核")) {
            tags.add("造价审核");
        }
        tags.add("核心链路");
        return new ArrayList<>(tags);
    }

    private List<NeighborFeature> buildNeighbors(FeatureKey parts, HierarchyNode featureNode, List<String> dataSources) {
        try {
            String parentId = featureNode != null ? featureNode.getParentId() : null;
            if (parentId == null) {
                parentId = hierarchyStore.listNodes(NodeLevel.scene, null, null, false).stream()
                        .filter(node -> parts.scene().equals(node.getName()))
                        .filter(node -> {
                            List<String> path = node.getPathNames();
                            return path != null && path.size() >= 2
                                    && parts.domain().equals(path.get(0))
                                    && parts.system().equals(path.get(1));
                        })
                        .map(HierarchyNode::getId)
                        .findFirst()
                        .orElse(null);
            }
            if (parentId == null) {
                return List.of();
            }
            List<NeighborFeature> neighbors = new ArrayList<>();
            for (HierarchyNode sibling : hierarchyStore.listNodes(NodeLevel.feature, parentId, null, false)) {
                if (sibling.getStatus() == NodeStatus.disabled) {
                    continue;
                }
                if (parts.feature().equals(sibling.getName())) {
                    continue;
                }
                String siblingKey = sibling.getFeatureKey();
                if (siblingKey == null || siblingKey.isBlank()) {
                    siblingKey = FeatureKey.join(parts.domain(), parts.system(), parts.scene(), sibling.getName());
                }
                neighbors.add(new NeighborFeature(siblingKey, sibling.getName()));
            }
            return neighbors;
        } catch (RuntimeException ex) {
            dataSources.add("neighbors:degraded");
            return List.of();
        }
    }

    private BusinessView buildBusinessView(
            FeatureKey parts,
            List<CaseAsset> cases,
            TechMappingResult tech,
            List<String> dataSources
    ) {
        BusinessView view = new BusinessView();
        Map<String, ScenarioItem> scenarios = new LinkedHashMap<>();
        List<CaseItem> caseItems = new ArrayList<>();
        List<ScriptItem> scripts = new ArrayList<>();

        for (CaseAsset asset : cases) {
            String scenarioName = asset.getTestScenario() == null || asset.getTestScenario().isBlank()
                    ? "未分类场景"
                    : asset.getTestScenario();
            ScenarioItem scenario = scenarios.computeIfAbsent(scenarioName, name -> newScenario(name, "covered"));
            scenario.setCaseCount(scenario.getCaseCount() + 1);

            CaseItem caseItem = new CaseItem();
            caseItem.setId(asset.getId());
            caseItem.setName(asset.getName());
            caseItem.setPriority(asset.getPriority());
            caseItem.setTestScenario(scenarioName);
            caseItem.setConfidence(asset.getConfidence());
            caseItem.setMountAdvice(mountAdvice(asset.getConfidence()));
            caseItem.setApi(asset.getApi());
            caseItem.setOriginalCaseId(asset.getOriginalCaseId());
            caseItem.setStep(asset.getStep());
            caseItem.setExpected(asset.getExpected());
            caseItem.setFlowNode(asset.getFlowNode());
            caseItem.setSourceType(asset.getSourceType());
            caseItem.setSource(SOURCE_OFFICIAL_CASES);
            caseItems.add(caseItem);

            ScriptItem script = deriveScriptFromCase(asset, tech);
            if (script != null) {
                scripts.add(script);
            }
        }

        if (scenarios.isEmpty()) {
            ScenarioItem gapScene = newScenario(parts.scene(), "gap");
            scenarios.put(gapScene.getName(), gapScene);
            dataSources.add("scenarios:gap");
        }

        for (ScenarioItem scenario : scenarios.values()) {
            if (scenario.getCaseCount() == 0) {
                scenario.setCoverageStatus("gap");
            } else if (scenario.getCaseCount() < 2) {
                scenario.setCoverageStatus("partial");
            } else {
                scenario.setCoverageStatus("covered");
            }
        }

        if (scripts.isEmpty()) {
            scripts.addAll(deriveScriptsFromTech(tech));
            if (!scripts.isEmpty()) {
                dataSources.add("scripts:" + SOURCE_DERIVED_TECH);
            }
        } else {
            dataSources.add("scripts:" + SOURCE_DERIVED_CASE);
        }

        List<DataTemplateItem> templates = new ArrayList<>();
        for (ScenarioItem scenario : scenarios.values()) {
            DataTemplateItem template = new DataTemplateItem();
            template.setId("DT-" + scenario.getId());
            if (scenario.getCaseCount() == 0) {
                template.setName(scenario.getName() + "测试数据（待补）");
                template.setSource(SOURCE_DERIVED_SCENE);
            } else {
                template.setName(scenario.getName() + "测试数据");
                template.setSource(SOURCE_DERIVED_CASE);
            }
            template.setLinkedScenarioId(scenario.getId());
            templates.add(template);
        }
        dataSources.add("data-templates:" + (cases.isEmpty() ? SOURCE_DERIVED_SCENE : SOURCE_DERIVED_CASE));

        ExecutionItem execution = new ExecutionItem();
        execution.setId("EXEC-UNAVAILABLE");
        execution.setLabel("执行平台未接入");
        execution.setResults(List.of());
        execution.setLastRunAt(null);
        execution.setSource(SOURCE_UNAVAILABLE);
        dataSources.add("executions:" + SOURCE_UNAVAILABLE);

        view.setScenarios(new ArrayList<>(scenarios.values()));
        view.setCases(caseItems);
        view.setScripts(scripts);
        view.setDataTemplates(templates);
        view.setExecutions(List.of(execution));
        return view;
    }

    private ScriptItem deriveScriptFromCase(CaseAsset asset, TechMappingResult tech) {
        String apiLabel = asset.getApi();
        String source = SOURCE_DERIVED_CASE;
        if ((apiLabel == null || apiLabel.isBlank()) && tech != null && !tech.getApis().isEmpty()) {
            apiLabel = tech.getApis().get(0).getLabel();
            source = SOURCE_DERIVED_TECH;
        }
        if (apiLabel == null || apiLabel.isBlank()) {
            return null;
        }
        ScriptItem script = scriptFromApiLabel("SCRIPT-" + asset.getId(), apiLabel, asset.getId(), source);
        return script;
    }

    private List<ScriptItem> deriveScriptsFromTech(TechMappingResult tech) {
        if (tech == null || tech.getApis() == null || tech.getApis().isEmpty()) {
            return List.of();
        }
        List<ScriptItem> scripts = new ArrayList<>();
        int index = 1;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (ResolvedApi api : tech.getApis()) {
            String label = api.getLabel() == null || api.getLabel().isBlank()
                    ? (api.getMethod() + " " + api.getPath()).trim()
                    : api.getLabel();
            if (!seen.add(label)) {
                continue;
            }
            scripts.add(scriptFromApiLabel("SCRIPT-TECH-" + index, label, null, SOURCE_DERIVED_TECH));
            index++;
        }
        return scripts;
    }

    private static ScriptItem scriptFromApiLabel(String id, String apiLabel, String linkedCaseId, String source) {
        String path = apiLabel;
        int space = apiLabel.lastIndexOf(' ');
        if (space >= 0) {
            path = apiLabel.substring(space + 1);
        }
        String fileName = path.replaceFirst("^/", "").replace('/', '_') + ".json";
        ScriptItem script = new ScriptItem();
        script.setId(id);
        script.setName(fileName);
        script.setType("API");
        script.setStatus("derived");
        script.setLinkedCaseId(linkedCaseId);
        script.setSource(source);
        return script;
    }

    private TechView buildTechView(FeatureKey parts, TechMappingResult tech, List<String> dataSources) {
        TechView view = new TechView();
        if (tech == null) {
            view.setSource("unavailable");
            dataSources.add("tech-view:unavailable");
            return view;
        }
        view.setSource(tech.getSource() == null ? "tech-mapping" : tech.getSource());
        if (tech.getService() != null && !tech.getService().isBlank()) {
            ServiceItem primary = new ServiceItem();
            primary.setName(tech.getService());
            primary.setRole("primary");
            view.setServices(List.of(primary));
        }
        List<FlowNodeItem> flowNodes = new ArrayList<>();
        List<String> flowNodeNames = new ArrayList<>();
        for (ResolvedFlowNode node : tech.getFlowNodes()) {
            FlowNodeItem item = new FlowNodeItem();
            item.setName(node.getName());
            item.setRisk(node.getRisk());
            item.setPrimary(node.isPrimary());
            flowNodes.add(item);
            if (node.getName() != null && !node.getName().isBlank()) {
                flowNodeNames.add(node.getName());
            }
        }
        view.setFlowNodes(flowNodes);

        List<ApiItem> apis = new ArrayList<>();
        List<CodeModuleItem> modules = new ArrayList<>();
        LinkedHashSet<String> moduleNames = new LinkedHashSet<>();
        for (ResolvedApi api : tech.getApis()) {
            ApiItem item = new ApiItem();
            item.setMethod(api.getMethod());
            item.setPath(api.getPath());
            item.setLabel(api.getLabel());
            item.setRelation(api.getRelation());
            item.setController(api.getController());
            item.setConfidence(api.isFallback() ? 40 : 90);
            apis.add(item);
            if (api.getController() != null && !api.getController().isBlank() && moduleNames.add(api.getController())) {
                CodeModuleItem module = new CodeModuleItem();
                module.setName(api.getController());
                module.setType("controller");
                modules.add(module);
            }
        }
        view.setApis(apis);
        view.setCodeModules(modules);

        List<String> tables = knowledgeCatalog.findTables(parts.scene(), flowNodeNames);
        view.setTables(tables);
        if (tables.isEmpty()) {
            dataSources.add("tables:unavailable");
        } else {
            dataSources.add("tables:" + knowledgeCatalog.getSource());
        }
        view.setMessages(List.of());
        dataSources.add("messages:" + SOURCE_UNAVAILABLE);
        return view;
    }

    private static ScenarioItem newScenario(String name, String coverageStatus) {
        ScenarioItem item = new ScenarioItem();
        item.setId("SC-" + slug(name));
        item.setName(name);
        item.setCoverageStatus(coverageStatus);
        item.setCaseCount(0);
        return item;
    }

    private static List<MapConsumer> defaultConsumers() {
        List<MapConsumer> consumers = new ArrayList<>();
        consumers.add(consumer("自动化测试平台", "按功能点拉取回归包"));
        consumers.add(consumer("质量报告", "覆盖率与缺陷趋势"));
        return consumers;
    }

    private static MapConsumer consumer(String platform, String usage) {
        MapConsumer item = new MapConsumer();
        item.setPlatform(platform);
        item.setUsage(usage);
        return item;
    }

    private static String mountAdvice(Integer confidence) {
        if (confidence == null) {
            return "待确认";
        }
        if (confidence >= 85) {
            return "自动挂载";
        }
        if (confidence >= 70) {
            return "待抽检";
        }
        return "人工确认";
    }

    private static String mapIdOf(String featureKey) {
        return "fm_" + Integer.toHexString(featureKey.hashCode());
    }

    private static String slug(String text) {
        return text.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]+", "_");
    }

    private static String stringMeta(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
