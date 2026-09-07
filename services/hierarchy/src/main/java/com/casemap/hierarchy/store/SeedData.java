package com.casemap.hierarchy.store;

import com.casemap.hierarchy.featurekey.FeatureKey;
import com.casemap.hierarchy.model.HierarchyNode;
import com.casemap.hierarchy.model.NodeLevel;
import com.casemap.hierarchy.model.NodeStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SeedData {

    private SeedData() {
    }

    public static List<HierarchyNode> quoteNodes() {
        String now = Instant.now().toString();
        List<HierarchyNode> nodes = new ArrayList<>();

        HierarchyNode domain = node("dom-home", NodeLevel.domain, "家装", null, "home",
                "家装事业线", 10, List.of("家装"), now);
        HierarchyNode system = node("sys-quote", NodeLevel.system, "报价", domain.getId(), "quote",
                "报价系统", 10, List.of("家装", "报价"), now);
        nodes.add(domain);
        nodes.add(system);

        Map<String, List<String>> scenes = new LinkedHashMap<>();
        scenes.put("金额计算与汇总", List.of("数量价汇总", "固定价汇总", "去利润价计算", "分组汇总"));
        scenes.put("造价提交与审核", List.of("自动审核判定", "提交审核", "审核拒绝回退"));
        scenes.put("参数解析与输入精度", List.of("参数必填校验", "输入精度校验"));

        int sceneIndex = 0;
        for (Map.Entry<String, List<String>> entry : scenes.entrySet()) {
            sceneIndex++;
            String sceneId = "scene-" + sceneIndex;
            HierarchyNode scene = node(sceneId, NodeLevel.scene, entry.getKey(), system.getId(), null,
                    "", sceneIndex * 10, List.of("家装", "报价", entry.getKey()), now);
            nodes.add(scene);
            int featureIndex = 0;
            for (String featureName : entry.getValue()) {
                featureIndex++;
                List<String> path = List.of("家装", "报价", entry.getKey(), featureName);
                HierarchyNode feature = node(
                        "feat-" + sceneIndex + "-" + featureIndex,
                        NodeLevel.feature,
                        featureName,
                        sceneId,
                        null,
                        "",
                        featureIndex * 10,
                        path,
                        now
                );
                feature.setFeatureKey(FeatureKey.join(path.get(0), path.get(1), path.get(2), path.get(3)));
                feature.getMeta().put("seed", true);
                nodes.add(feature);
            }
        }

        Object[][] extras = {
                {"dom-shoufang", "收房", "shoufang", 20},
                {"dom-chufang", "出房", "chufang", 30},
                {"dom-jiafu", "家服", "jiafu", 40},
                {"dom-lingzhi", "灵之", "lingzhi", 50},
                {"dom-qixin", "企信", "qixin", 60}
        };
        for (Object[] row : extras) {
            nodes.add(node(
                    (String) row[0],
                    NodeLevel.domain,
                    (String) row[1],
                    null,
                    (String) row[2],
                    row[1] + "领域（待补充系统）",
                    (Integer) row[3],
                    List.of((String) row[1]),
                    now
            ));
        }
        return nodes;
    }

    private static HierarchyNode node(
            String id,
            NodeLevel level,
            String name,
            String parentId,
            String code,
            String description,
            int sortOrder,
            List<String> pathNames,
            String now
    ) {
        HierarchyNode node = new HierarchyNode();
        node.setId(id);
        node.setLevel(level);
        node.setName(name);
        node.setParentId(parentId);
        node.setCode(code);
        node.setDescription(description);
        node.setSortOrder(sortOrder);
        node.setStatus(NodeStatus.enabled);
        node.setPathNames(new ArrayList<>(pathNames));
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
        return node;
    }
}
