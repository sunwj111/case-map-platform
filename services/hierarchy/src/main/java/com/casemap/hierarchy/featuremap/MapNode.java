package com.casemap.hierarchy.featuremap;

/**
 * 图谱 / 列组件统一节点模型（M1-S03）。
 */
public class MapNode {

    private String id;
    private MapNodeType type;
    private String title;
    private MapNodeStatus status = MapNodeStatus.ready;
    private Integer confidence;
    private Object payload;

    public MapNode() {
    }

    public MapNode(String id, MapNodeType type, String title) {
        this.id = id;
        this.type = type;
        this.title = title;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public MapNodeType getType() {
        return type;
    }

    public void setType(MapNodeType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public MapNodeStatus getStatus() {
        return status;
    }

    public void setStatus(MapNodeStatus status) {
        this.status = status;
    }

    public Integer getConfidence() {
        return confidence;
    }

    public void setConfidence(Integer confidence) {
        this.confidence = confidence;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }
}
