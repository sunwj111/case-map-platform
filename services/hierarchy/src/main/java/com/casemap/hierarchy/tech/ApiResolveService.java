package com.casemap.hierarchy.tech;

import com.casemap.hierarchy.featurekey.FeatureKey;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApiResolveService {

    private static final Map<String, Integer> RELATION_RANK = Map.of(
            "主接口", 0,
            "校验接口", 1,
            "查询接口", 2,
            "配套接口", 3
    );

    private final FeatureApiMapCatalog apiMapCatalog;
    private final FlowNodeCatalog flowNodeCatalog;

    public ApiResolveService(FeatureApiMapCatalog apiMapCatalog, FlowNodeCatalog flowNodeCatalog) {
        this.apiMapCatalog = apiMapCatalog;
        this.flowNodeCatalog = flowNodeCatalog;
    }

    public TechMappingResult resolve(String featureKey, String feature, String scene) {
        String resolvedFeature = trimToNull(feature);
        String resolvedScene = trimToNull(scene);
        if (trimToNull(featureKey) != null) {
            FeatureKey parsed = FeatureKey.parse(featureKey);
            if (resolvedFeature == null) {
                resolvedFeature = parsed.feature();
            }
            if (resolvedScene == null) {
                resolvedScene = parsed.scene();
            }
        }
        if (resolvedFeature == null) {
            throw new IllegalArgumentException("请提供 feature 或 featureKey");
        }

        List<FeatureApiMapItem> byFeature = new ArrayList<>();
        List<FeatureApiMapItem> byNode = new ArrayList<>();
        for (FeatureApiMapItem item : apiMapCatalog.listItems()) {
            if (FeatureApiMapCatalog.hitsFeature(item, resolvedFeature)) {
                byFeature.add(item);
            } else if (FeatureApiMapCatalog.hitsNode(item, resolvedScene)) {
                byNode.add(item);
            }
        }
        List<FeatureApiMapItem> selected = byFeature.isEmpty() ? byNode : byFeature;
        selected.sort(Comparator.comparingInt(item -> relationRank(item.getRelation())));

        FeatureApiMapFile mapFile = apiMapCatalog.getFile();
        TechMappingResult result = new TechMappingResult();
        result.setFeature(resolvedFeature);
        result.setScene(resolvedScene);
        result.setApp(mapFile.getApp());
        result.setService(mapFile.getService());
        result.setSource(mapFile.getSource());

        if (selected.isEmpty()) {
            result.setFallback(true);
            result.setApis(List.of(fallbackApi(resolvedFeature)));
            result.setFlowNodes(mergeFlowNodes(List.of(), resolvedScene));
            return result;
        }

        List<ResolvedApi> apis = new ArrayList<>();
        for (FeatureApiMapItem item : selected) {
            apis.addAll(expand(item, false));
        }
        result.setFallback(false);
        result.setApis(apis);
        result.setFlowNodes(mergeFlowNodes(selected, resolvedScene));
        return result;
    }

    public List<QuoteFlowNode> listFlowNodes() {
        return flowNodeCatalog.listAll();
    }

    private List<ResolvedFlowNode> mergeFlowNodes(List<FeatureApiMapItem> selected, String scene) {
        Map<String, ResolvedFlowNode> merged = new LinkedHashMap<>();
        for (FeatureApiMapItem item : selected) {
            List<String> names = new ArrayList<>();
            if (item.getPrimaryNode() != null && !item.getPrimaryNode().isBlank()) {
                names.add(item.getPrimaryNode());
            }
            names.addAll(item.getNodes());
            for (String nodeName : names) {
                putFlowNode(merged, nodeName, scene);
            }
        }
        if (scene != null) {
            putFlowNode(merged, scene, scene);
        }
        return new ArrayList<>(merged.values());
    }

    private void putFlowNode(Map<String, ResolvedFlowNode> merged, String nodeName, String scene) {
        if (nodeName == null || nodeName.isBlank() || merged.containsKey(nodeName)) {
            return;
        }
        QuoteFlowNode catalogNode = flowNodeCatalog.findByName(nodeName).orElse(null);
        ResolvedFlowNode resolved = new ResolvedFlowNode();
        resolved.setName(nodeName);
        resolved.setPrimary(nodeName.equals(scene));
        if (catalogNode != null) {
            resolved.setRisk(catalogNode.getRisk());
            resolved.setDescription(catalogNode.getDescription());
            resolved.setSource(flowNodeCatalog.getSource());
        } else {
            resolved.setSource("feature_api_map");
        }
        merged.put(nodeName, resolved);
    }

    private static List<ResolvedApi> expand(FeatureApiMapItem item, boolean fallback) {
        List<ResolvedApi> rows = new ArrayList<>();
        List<String> paths = item.getPaths() == null || item.getPaths().isEmpty()
                ? List.of("")
                : item.getPaths();
        String method = item.getMethod() == null || item.getMethod().isBlank() ? "POST" : item.getMethod();
        for (String path : paths) {
            ResolvedApi api = new ResolvedApi();
            api.setMethod(method);
            api.setPath(path);
            api.setLabel((method + " " + path).trim());
            api.setRelation(item.getRelation());
            api.setController(item.getController());
            api.setPrimaryNode(item.getPrimaryNode());
            api.setFallback(fallback);
            rows.add(api);
        }
        return rows;
    }

    private static ResolvedApi fallbackApi(String feature) {
        String path = guessPath(feature);
        ResolvedApi api = new ResolvedApi();
        api.setMethod("POST");
        api.setPath(path);
        api.setLabel("POST " + path);
        api.setRelation("回退接口");
        api.setFallback(true);
        return api;
    }

    static String guessPath(String feature) {
        String text = feature == null ? "" : feature;
        if (containsAny(text, "数量价", "固定价", "租赁价", "去利润", "分组", "金额", "汇总", "报价单生成", "价格来源", "场景", "类型识别")) {
            return "/quotation/offer";
        }
        if (containsAny(text, "参数", "精度", "必填")) {
            return "/quotation/checkParamBeforeSubmit";
        }
        if (containsAny(text, "物料", "带出", "配件")) {
            return "/quotation/bim/offer";
        }
        if (containsAny(text, "造价", "明细落库")) {
            return "/quotation/createDesignCost";
        }
        if (containsAny(text, "提交审核", "自动审核", "审核拒绝")) {
            return "/quotation/submitDesignCost";
        }
        if (containsAny(text, "库存", "失效", "预占")) {
            return "/quotation/confirmDesignCost";
        }
        return "/quotation/offer";
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static int relationRank(String relation) {
        if (relation == null) {
            return 9;
        }
        for (Map.Entry<String, Integer> entry : RELATION_RANK.entrySet()) {
            if (relation.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return 9;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
