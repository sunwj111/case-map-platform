package com.casemap.hierarchy.web;

import com.casemap.hierarchy.featurekey.FeatureKey;
import com.casemap.hierarchy.featuremap.FeatureMapDto;
import com.casemap.hierarchy.featuremap.FeatureMapQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feature-maps")
public class FeatureMapController {

    private final FeatureMapQueryService featureMapQueryService;

    public FeatureMapController(FeatureMapQueryService featureMapQueryService) {
        this.featureMapQueryService = featureMapQueryService;
    }

    @GetMapping("/{*featureKey}")
    public FeatureMapDto get(@PathVariable("featureKey") String featureKey) {
        String key = FeatureKey.fromPathVariable(featureKey);
        return featureMapQueryService.findByFeatureKey(key)
                .orElseThrow(() -> new NotFoundException("功能点不存在：" + key));
    }
}
