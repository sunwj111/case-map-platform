package com.casemap.hierarchy.featuremap;

import com.casemap.hierarchy.asset.CaseAsset;
import com.casemap.hierarchy.asset.CaseQueryService;
import com.casemap.hierarchy.featurekey.FeatureKey;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 功能点地图组装主流程（M1-S04 / M1-S05）。
 * 多数据源聚合；任一源缺失时降级填充，不抛出 500。
 */
@Service
public class FeatureMapAssembler {

    private static final String MAP_VERSION = "1.0.0";
    private static final String DEFAULT_BASELINE = "BL-DRAFT";
    private static final String PROCESS_NAME = "报价流程";

    private final HierarchyStore hierarchyStore;
    private final CaseQueryService caseQueryService;
    private final ApiResolveService apiResolveService;

    public FeatureMapAssembler(
            HierarchyStore hierarchyStore,
            CaseQueryService caseQueryService,
            ApiResolveService apiResolveService
    ) {
        this.hierarchyStore = hierarchyStore;
        this.caseQueryService = caseQueryService;
        this.apiResolveService = apiResolveService;
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
        map.setMeta(buildMeta(featureKey, featureNode.orElse(null), dataSources));
        map.setSpine(buildSpine(parts, featureNode.orElse(null), dataSources));
        map.setBusinessView(buildBusinessView(cases, tech));
        map.setTechView(buildTechView(tech));
        map.setRiskView(buildRiskView(cases, tech, map.getSpine()));
        map.setSummary(buildSummary(cases, map.getBusinessView()));
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
            CaseQueryService.CaseQueryResult result = caseQueryService.query(featureKey, null, null);
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
        FeatureMapMeta meta = new FeatureMapMeta();
        meta.setMapId(mapIdOf(featureKey));
        meta.setFeatureKey(featureKey);
        meta.setVersion(MAP_VERSION);
        meta.setBaseline(DEFAULT_BASELINE);
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
        spine.setProcessName(PROCESS_NAME);
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

    private BusinessView buildBusinessView(List<CaseAsset> cases, TechMappingResult tech) {
        BusinessView view = new BusinessView();
        Map<String, ScenarioItem> scenarios = new LinkedHashMap<>();
        List<CaseItem> caseItems = new ArrayList<>();
        List<ScriptItem> scripts = new ArrayList<>();

        for (CaseAsset asset : cases) {
            String scenarioName = asset.getTestScenario() == null || asset.getTestScenario().isBlank()
                    ? "未分类场景"
                    : asset.getTestScenario();
            ScenarioItem scenario = scenarios.computeIfAbsent(scenarioName, name -> {
                ScenarioItem item = new ScenarioItem();
                item.setId("SC-" + slug(name));
                item.setName(name);
                item.setCoverageStatus("covered");
                item.setCaseCount(0);
                return item;
            });
            scenario.setCaseCount(scenario.getCaseCount() + 1);

            CaseItem caseItem = new CaseItem();
            caseItem.setId(asset.getId());
            caseItem.setName(asset.getName());
            caseItem.setPriority(asset.getPriority());
            caseItem.setTestScenario(scenarioName);
            caseItem.setConfidence(asset.getConfidence());
            caseItem.setMountAdvice(mountAdvice(asset.getConfidence()));
            caseItem.setApi(asset.getApi());
            caseItems.add(caseItem);

            ScriptItem script = deriveScript(asset, tech);
            if (script != null) {
                scripts.add(script);
            }
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

        view.setScenarios(new ArrayList<>(scenarios.values()));
        view.setCases(caseItems);
        view.setScripts(scripts);
        view.setDataTemplates(List.of());
        view.setExecutions(List.of());
        return view;
    }

    private ScriptItem deriveScript(CaseAsset asset, TechMappingResult tech) {
        String apiLabel = asset.getApi();
        if ((apiLabel == null || apiLabel.isBlank()) && tech != null && !tech.getApis().isEmpty()) {
            apiLabel = tech.getApis().get(0).getLabel();
        }
        if (apiLabel == null || apiLabel.isBlank()) {
            return null;
        }
        String path = apiLabel;
        int slash = apiLabel.lastIndexOf(' ');
        if (slash >= 0) {
            path = apiLabel.substring(slash + 1);
        }
        String fileName = path.replaceFirst("^/", "").replace('/', '_') + ".json";
        ScriptItem script = new ScriptItem();
        script.setId("SCRIPT-" + asset.getId());
        script.setName(fileName);
        script.setType("API");
        script.setStatus("derived");
        script.setLinkedCaseId(asset.getId());
        return script;
    }

    private TechView buildTechView(TechMappingResult tech) {
        TechView view = new TechView();
        if (tech == null) {
            view.setSource("unavailable");
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
        for (ResolvedFlowNode node : tech.getFlowNodes()) {
            FlowNodeItem item = new FlowNodeItem();
            item.setName(node.getName());
            item.setRisk(node.getRisk());
            item.setPrimary(node.isPrimary());
            flowNodes.add(item);
        }
        view.setFlowNodes(flowNodes);

        List<ApiItem> apis = new ArrayList<>();
        for (ResolvedApi api : tech.getApis()) {
            ApiItem item = new ApiItem();
            item.setMethod(api.getMethod());
            item.setPath(api.getPath());
            item.setLabel(api.getLabel());
            item.setRelation(api.getRelation());
            item.setController(api.getController());
            item.setConfidence(api.isFallback() ? 40 : 90);
            apis.add(item);
        }
        view.setApis(apis);
        view.setTables(List.of());
        view.setMessages(List.of());
        view.setCodeModules(List.of());
        return view;
    }

    private RiskView buildRiskView(List<CaseAsset> cases, TechMappingResult tech, BusinessSpine spine) {
        RiskView view = new RiskView();
        view.setRules(List.of());
        view.setDefects(List.of());
        List<RiskTagItem> tags = new ArrayList<>();
        boolean highRisk = tech != null && tech.getFlowNodes().stream()
                .anyMatch(node -> node.getRisk() != null && node.getRisk().contains("高"));
        if (highRisk) {
            tags.add(tag("risk", "高风险", "high"));
        }
        if (spine.getValueTags().contains("核心链路")) {
            tags.add(tag("chain", "核心链路", "high"));
        }
        if (cases.isEmpty()) {
            tags.add(tag("gap", "无正式用例", "medium"));
        }
        view.setTags(tags);
        return view;
    }

    private QualitySummary buildSummary(List<CaseAsset> cases, BusinessView businessView) {
        QualitySummary summary = new QualitySummary();
        summary.setLinkedCaseCount(cases.size());

        Map<String, Object> distribution = new LinkedHashMap<>();
        int p0 = 0;
        int p1 = 0;
        int p2 = 0;
        int p3 = 0;
        for (CaseAsset asset : cases) {
            String priority = asset.getPriority() == null ? "" : asset.getPriority().toUpperCase(Locale.ROOT);
            if (priority.startsWith("P0")) {
                p0++;
            } else if (priority.startsWith("P1")) {
                p1++;
            } else if (priority.startsWith("P2")) {
                p2++;
            } else if (priority.startsWith("P3")) {
                p3++;
            }
        }
        int total = Math.max(cases.size(), 1);
        distribution.put("P0", p0);
        distribution.put("P1", p1);
        distribution.put("P2", p2);
        distribution.put("P3", p3);
        distribution.put("P0Rate", roundRate(p0, total));
        distribution.put("P1Rate", roundRate(p1, total));
        distribution.put("P2Rate", roundRate(p2, total));
        distribution.put("P3Rate", roundRate(p3, total));
        summary.setPriorityDistribution(distribution);

        long withApi = cases.stream()
                .filter(asset -> asset.getApi() != null && !asset.getApi().isBlank())
                .count();
        summary.setAutomationCoverage(cases.isEmpty() ? 0.0 : roundRate((int) withApi, cases.size()));
        summary.setDefectCount30d(0);
        long gapScenes = businessView.getScenarios().stream()
                .filter(scenario -> "gap".equals(scenario.getCoverageStatus()))
                .count();
        summary.setGapCount((int) gapScenes);
        if (cases.isEmpty()) {
            summary.setCoverageStatus("gap");
        } else if (gapScenes > 0 || summary.getAutomationCoverage() < 0.7) {
            summary.setCoverageStatus("partial");
        } else {
            summary.setCoverageStatus("covered");
        }
        return summary;
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

    private static RiskTagItem tag(String type, String label, String level) {
        RiskTagItem item = new RiskTagItem();
        item.setType(type);
        item.setLabel(label);
        item.setLevel(level);
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

    private static double roundRate(int count, int total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round((count * 1000.0) / total) / 1000.0;
    }
}
