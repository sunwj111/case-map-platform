package com.casemap.hierarchy.model;

import java.util.ArrayList;
import java.util.List;

public class HierarchyTreeNode {

    private String id;
    private NodeLevel level;
    private String name;
    private NodeStatus status;
    private String featureKey;
    private List<HierarchyTreeNode> children = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public NodeLevel getLevel() {
        return level;
    }

    public void setLevel(NodeLevel level) {
        this.level = level;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    public String getFeatureKey() {
        return featureKey;
    }

    public void setFeatureKey(String featureKey) {
        this.featureKey = featureKey;
    }

    public List<HierarchyTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<HierarchyTreeNode> children) {
        this.children = children;
    }
}
