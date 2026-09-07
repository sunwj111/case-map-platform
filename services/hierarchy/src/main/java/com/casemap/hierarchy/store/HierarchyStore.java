package com.casemap.hierarchy.store;

import com.casemap.hierarchy.featurekey.FeatureKey;
import com.casemap.hierarchy.model.CascadeOptions;
import com.casemap.hierarchy.model.CreateNodeRequest;
import com.casemap.hierarchy.model.HierarchyNode;
import com.casemap.hierarchy.model.HierarchyTreeNode;
import com.casemap.hierarchy.model.NodeLevel;
import com.casemap.hierarchy.model.NodeStatus;
import com.casemap.hierarchy.model.UpdateNodeRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class HierarchyStore {

    private final ObjectMapper objectMapper;
    private final Path dataFile;
    private final Map<String, HierarchyNode> nodes = new ConcurrentHashMap<>();

    public HierarchyStore(ObjectMapper objectMapper,
                          @Value("${hierarchy.data-file:data/hierarchy_store.json}") String dataFile) {
        this.objectMapper = objectMapper;
        this.dataFile = Path.of(dataFile);
    }

    @PostConstruct
    public void init() throws IOException {
        if (Files.exists(dataFile)) {
            load();
        }
        if (nodes.isEmpty()) {
            replaceAll(SeedData.quoteNodes());
        }
    }

    public synchronized List<HierarchyNode> listNodes(NodeLevel level, String parentId, NodeStatus status, boolean includeDisabled) {
        return nodes.values().stream()
                .filter(node -> level == null || node.getLevel() == level)
                .filter(node -> parentId == null || Objects.equals(parentId, node.getParentId()))
                .filter(node -> {
                    if (status != null) {
                        return node.getStatus() == status;
                    }
                    return includeDisabled || node.getStatus() != NodeStatus.disabled;
                })
                .sorted(Comparator.comparingInt(HierarchyNode::getSortOrder).thenComparing(HierarchyNode::getName))
                .collect(Collectors.toList());
    }

    public Optional<HierarchyNode> get(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    public Optional<HierarchyNode> getByFeatureKey(String featureKey) {
        String key = FeatureKey.parse(featureKey).toKey();
        return nodes.values().stream()
                .filter(node -> node.getLevel() == NodeLevel.feature)
                .filter(node -> key.equals(node.getFeatureKey()))
                .findFirst();
    }

    public synchronized HierarchyNode create(CreateNodeRequest request) {
        String name = normalizeName(request.getName());
        List<String> pathNames = pathNamesForCreate(request.getLevel(), name, request.getParentId());
        for (HierarchyNode existing : nodes.values()) {
            if (existing.getLevel() == request.getLevel()
                    && Objects.equals(existing.getParentId(), request.getParentId())
                    && existing.getName().equals(name)
                    && existing.getStatus() != NodeStatus.disabled) {
                throw new IllegalArgumentException("同级已存在「" + name + "」");
            }
        }

        HierarchyNode node = new HierarchyNode();
        node.setId(UUID.randomUUID().toString());
        node.setLevel(request.getLevel());
        node.setName(name);
        node.setCode(request.getCode());
        node.setParentId(request.getParentId());
        node.setStatus(request.getStatus() == null ? NodeStatus.enabled : request.getStatus());
        node.setDescription(request.getDescription() == null ? "" : request.getDescription());
        node.setSortOrder(request.getSortOrder());
        node.setPathNames(pathNames);
        node.setMeta(request.getMeta() == null ? new HashMap<>() : new HashMap<>(request.getMeta()));
        if (request.getLevel() == NodeLevel.feature) {
            node.setFeatureKey(FeatureKey.join(pathNames.get(0), pathNames.get(1), pathNames.get(2), pathNames.get(3)));
        }
        String now = Instant.now().toString();
        node.setCreatedAt(now);
        node.setUpdatedAt(now);
        nodes.put(node.getId(), node);
        save();
        return node;
    }

    public synchronized HierarchyNode update(String nodeId, UpdateNodeRequest request) {
        HierarchyNode node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在");
        }
        boolean rename = request.getName() != null && !request.getName().equals(node.getName());
        if (request.getName() != null) {
            node.setName(normalizeName(request.getName()));
        }
        if (request.getCode() != null) {
            node.setCode(request.getCode());
        }
        if (request.getDescription() != null) {
            node.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            node.setStatus(request.getStatus());
        }
        if (request.getSortOrder() != null) {
            node.setSortOrder(request.getSortOrder());
        }
        if (request.getMeta() != null) {
            node.setMeta(new HashMap<>(request.getMeta()));
        }
        node.setUpdatedAt(Instant.now().toString());
        if (rename) {
            renameCascade(node);
        }
        nodes.put(nodeId, node);
        save();
        return node;
    }

    public HierarchyNode disable(String nodeId) {
        UpdateNodeRequest request = new UpdateNodeRequest();
        request.setStatus(NodeStatus.disabled);
        return update(nodeId, request);
    }

    public List<HierarchyTreeNode> asTree(boolean includeDisabled) {
        List<HierarchyNode> all = listNodes(null, null, null, includeDisabled);
        Map<String, List<HierarchyNode>> byParent = new HashMap<>();
        for (HierarchyNode node : all) {
            byParent.computeIfAbsent(node.getParentId(), key -> new ArrayList<>()).add(node);
        }
        return buildTree(null, byParent);
    }

    public CascadeOptions cascadeOptions(String domain, String system, String scene) {
        CascadeOptions options = new CascadeOptions();
        options.setDomains(listNodes(NodeLevel.domain, null, null, false).stream()
                .map(HierarchyNode::getName).collect(Collectors.toList()));

        HierarchyNode domainNode = findByName(NodeLevel.domain, null, domain).orElse(null);
        if (domainNode != null) {
            options.setSystems(listNodes(NodeLevel.system, domainNode.getId(), null, false).stream()
                    .map(HierarchyNode::getName).collect(Collectors.toList()));
        }

        HierarchyNode systemNode = domainNode == null ? null
                : findByName(NodeLevel.system, domainNode.getId(), system).orElse(null);
        if (systemNode != null) {
            options.setScenes(listNodes(NodeLevel.scene, systemNode.getId(), null, false).stream()
                    .map(HierarchyNode::getName).collect(Collectors.toList()));
        }

        HierarchyNode sceneNode = systemNode == null ? null
                : findByName(NodeLevel.scene, systemNode.getId(), scene).orElse(null);
        if (sceneNode != null) {
            List<Map<String, String>> features = new ArrayList<>();
            for (HierarchyNode feature : listNodes(NodeLevel.feature, sceneNode.getId(), null, false)) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("name", feature.getName());
                item.put("featureKey", feature.getFeatureKey() == null ? "" : feature.getFeatureKey());
                features.add(item);
            }
            options.setFeatures(features);
        }
        return options;
    }

    public synchronized void replaceAll(List<HierarchyNode> seed) {
        nodes.clear();
        for (HierarchyNode node : seed) {
            nodes.put(node.getId(), node);
        }
        save();
    }

    public int size() {
        return nodes.size();
    }

    private Optional<HierarchyNode> findByName(NodeLevel level, String parentId, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return listNodes(level, parentId, null, false).stream()
                .filter(node -> name.equals(node.getName()))
                .findFirst();
    }

    private List<HierarchyTreeNode> buildTree(String parentId, Map<String, List<HierarchyNode>> byParent) {
        List<HierarchyNode> children = byParent.getOrDefault(parentId, List.of());
        List<HierarchyTreeNode> result = new ArrayList<>();
        for (HierarchyNode node : children) {
            HierarchyTreeNode treeNode = new HierarchyTreeNode();
            treeNode.setId(node.getId());
            treeNode.setLevel(node.getLevel());
            treeNode.setName(node.getName());
            treeNode.setStatus(node.getStatus());
            treeNode.setFeatureKey(node.getFeatureKey());
            treeNode.setChildren(buildTree(node.getId(), byParent));
            result.add(treeNode);
        }
        return result;
    }

    private List<String> pathNamesForCreate(NodeLevel level, String name, String parentId) {
        if (level == NodeLevel.domain) {
            return List.of(name);
        }
        if (parentId == null || parentId.isBlank()) {
            throw new IllegalArgumentException(level + " 必须指定 parentId");
        }
        HierarchyNode parent = nodes.get(parentId);
        if (parent == null) {
            throw new IllegalArgumentException("父节点不存在");
        }
        NodeLevel expectedParent = switch (level) {
            case system -> NodeLevel.domain;
            case scene -> NodeLevel.system;
            case feature -> NodeLevel.scene;
            default -> throw new IllegalArgumentException("非法层级");
        };
        if (parent.getLevel() != expectedParent) {
            throw new IllegalArgumentException(level + " 的父级必须是 " + expectedParent);
        }
        List<String> path = new ArrayList<>(parent.getPathNames());
        path.add(name);
        return path;
    }

    private void renameCascade(HierarchyNode renamed) {
        int index = levelIndex(renamed.getLevel());
        List<String> newPath = new ArrayList<>(renamed.getPathNames());
        if (newPath.size() <= index) {
            newPath = new ArrayList<>(pathNamesForCreate(renamed.getLevel(), renamed.getName(), renamed.getParentId()));
        } else {
            newPath.set(index, renamed.getName());
        }
        renamed.setPathNames(newPath);
        if (renamed.getLevel() == NodeLevel.feature) {
            renamed.setFeatureKey(FeatureKey.join(newPath.get(0), newPath.get(1), newPath.get(2), newPath.get(3)));
        }
        walkRename(renamed.getId(), renamed.getPathNames());
    }

    private void walkRename(String parentId, List<String> parentPath) {
        for (HierarchyNode child : new ArrayList<>(nodes.values())) {
            if (!Objects.equals(child.getParentId(), parentId)) {
                continue;
            }
            List<String> childPath = new ArrayList<>(parentPath);
            childPath.add(child.getName());
            child.setPathNames(childPath);
            if (child.getLevel() == NodeLevel.feature) {
                child.setFeatureKey(FeatureKey.join(childPath.get(0), childPath.get(1), childPath.get(2), childPath.get(3)));
            }
            child.setUpdatedAt(Instant.now().toString());
            nodes.put(child.getId(), child);
            walkRename(child.getId(), childPath);
        }
    }

    private int levelIndex(NodeLevel level) {
        return switch (level) {
            case domain -> 0;
            case system -> 1;
            case scene -> 2;
            case feature -> 3;
        };
    }

    private String normalizeName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty() || value.contains("/")) {
            throw new IllegalArgumentException("name 非法");
        }
        return value;
    }

    private void load() throws IOException {
        Map<String, Object> payload = objectMapper.readValue(dataFile.toFile(), new TypeReference<>() {
        });
        Object rawNodes = payload.get("nodes");
        List<HierarchyNode> loaded = objectMapper.convertValue(rawNodes, new TypeReference<>() {
        });
        nodes.clear();
        if (loaded != null) {
            for (HierarchyNode node : loaded) {
                nodes.put(node.getId(), node);
            }
        }
    }

    private void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("version", 1);
            payload.put("updatedAt", Instant.now().toString());
            payload.put("nodes", listNodes(null, null, null, true));
            Path tmp = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), payload);
            Files.move(tmp, dataFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("保存层级数据失败", ex);
        }
    }
}
