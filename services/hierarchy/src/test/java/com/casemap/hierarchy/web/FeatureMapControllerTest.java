package com.casemap.hierarchy.web;

import com.casemap.hierarchy.featurekey.FeatureKey;
import com.casemap.hierarchy.featuremap.BusinessSpine;
import com.casemap.hierarchy.featuremap.BusinessView;
import com.casemap.hierarchy.featuremap.FeatureMapDto;
import com.casemap.hierarchy.featuremap.FeatureMapMeta;
import com.casemap.hierarchy.featuremap.FeatureMapQueryService;
import com.casemap.hierarchy.featuremap.QualitySummary;
import com.casemap.hierarchy.featuremap.RiskView;
import com.casemap.hierarchy.featuremap.TechView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FeatureMapController.class)
class FeatureMapControllerTest {

    private static final String KNOWN_KEY = "家装/报价/金额计算与汇总/数量价汇总";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FeatureMapQueryService featureMapQueryService;

    @Test
    void returnsCompleteMapForKnownFeature() throws Exception {
        when(featureMapQueryService.findByFeatureKey(KNOWN_KEY)).thenReturn(Optional.of(sampleMap()));

        mockMvc.perform(get("/api/v1/feature-maps/{featureKey}", KNOWN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.featureKey").value(KNOWN_KEY))
                .andExpect(jsonPath("$.meta.mapId").value("fm_quantity_price"))
                .andExpect(jsonPath("$.spine.domain").value("家装"))
                .andExpect(jsonPath("$.spine.app").value("报价"))
                .andExpect(jsonPath("$.spine.sceneName").value("金额计算与汇总"))
                .andExpect(jsonPath("$.spine.featureName").value("数量价汇总"))
                .andExpect(jsonPath("$.businessView").exists())
                .andExpect(jsonPath("$.techView").exists())
                .andExpect(jsonPath("$.riskView").exists())
                .andExpect(jsonPath("$.summary.linkedCaseCount").value(3));
    }

    @Test
    void returnsCompleteMapForUrlEncodedFeatureKey() throws Exception {
        when(featureMapQueryService.findByFeatureKey(KNOWN_KEY)).thenReturn(Optional.of(sampleMap()));

        mockMvc.perform(get("/api/v1/feature-maps/" + FeatureKey.encodeForUrl(KNOWN_KEY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.featureKey").value(KNOWN_KEY));
    }

    @Test
    void returnsNotFoundWhenFeatureMissing() throws Exception {
        String missingKey = "家装/报价/金额计算与汇总/不存在功能点XYZ";
        when(featureMapQueryService.findByFeatureKey(missingKey)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/feature-maps/{featureKey}", missingKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("功能点不存在：" + missingKey));
    }

    @Test
    void returnsBadRequestWhenFeatureKeyInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/feature-maps/{featureKey}", "只有两段/功能点"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    private static FeatureMapDto sampleMap() {
        FeatureMapDto map = new FeatureMapDto();
        FeatureMapMeta meta = new FeatureMapMeta();
        meta.setMapId("fm_quantity_price");
        meta.setFeatureKey(KNOWN_KEY);
        meta.setVersion("1.0.0");
        map.setMeta(meta);

        BusinessSpine spine = new BusinessSpine();
        spine.setDomain("家装");
        spine.setApp("报价");
        spine.setSceneName("金额计算与汇总");
        spine.setFeatureName("数量价汇总");
        map.setSpine(spine);

        map.setBusinessView(new BusinessView());
        map.setTechView(new TechView());
        map.setRiskView(new RiskView());
        QualitySummary summary = new QualitySummary();
        summary.setLinkedCaseCount(3);
        map.setSummary(summary);
        map.setConsumers(List.of());
        return map;
    }
}
