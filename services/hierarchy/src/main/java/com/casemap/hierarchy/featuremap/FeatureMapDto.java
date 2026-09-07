package com.casemap.hierarchy.featuremap;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能点地图主结构（M1-S02）。
 * 对应原型 {@code window.__FEATURE_MAP_SAMPLES__} 中的单条地图对象。
 */
public class FeatureMapDto {

    private FeatureMapMeta meta = new FeatureMapMeta();
    private BusinessSpine spine = new BusinessSpine();
    private BusinessView businessView = new BusinessView();
    private TechView techView = new TechView();
    private RiskView riskView = new RiskView();
    private QualitySummary summary = new QualitySummary();
    private List<MapConsumer> consumers = new ArrayList<>();

    public FeatureMapMeta getMeta() {
        return meta;
    }

    public void setMeta(FeatureMapMeta meta) {
        this.meta = meta;
    }

    public BusinessSpine getSpine() {
        return spine;
    }

    public void setSpine(BusinessSpine spine) {
        this.spine = spine;
    }

    public BusinessView getBusinessView() {
        return businessView;
    }

    public void setBusinessView(BusinessView businessView) {
        this.businessView = businessView;
    }

    public TechView getTechView() {
        return techView;
    }

    public void setTechView(TechView techView) {
        this.techView = techView;
    }

    public RiskView getRiskView() {
        return riskView;
    }

    public void setRiskView(RiskView riskView) {
        this.riskView = riskView;
    }

    public QualitySummary getSummary() {
        return summary;
    }

    public void setSummary(QualitySummary summary) {
        this.summary = summary;
    }

    public List<MapConsumer> getConsumers() {
        return consumers;
    }

    public void setConsumers(List<MapConsumer> consumers) {
        this.consumers = consumers;
    }
}
