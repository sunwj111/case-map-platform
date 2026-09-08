package com.casemap.hierarchy.featuremap;

import com.casemap.hierarchy.asset.CaseQueryService;
import com.casemap.hierarchy.featurekey.FeatureKey;
import com.casemap.hierarchy.store.HierarchyStore;
import com.casemap.hierarchy.tech.FeatureApiMapCatalog;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 功能点地图查询门面（E-08 / E-09）。
 * 层级节点存在，或正式资产/主数据可支撑合成时返回地图；否则 empty → 404。
 */
@Service
public class FeatureMapQueryService {

    private final HierarchyStore hierarchyStore;
    private final CaseQueryService caseQueryService;
    private final FeatureApiMapCatalog apiMapCatalog;
    private final FeatureMapAssembler assembler;

    public FeatureMapQueryService(
            HierarchyStore hierarchyStore,
            CaseQueryService caseQueryService,
            FeatureApiMapCatalog apiMapCatalog,
            FeatureMapAssembler assembler
    ) {
        this.hierarchyStore = hierarchyStore;
        this.caseQueryService = caseQueryService;
        this.apiMapCatalog = apiMapCatalog;
        this.assembler = assembler;
    }

    public Optional<FeatureMapDto> findByFeatureKey(String featureKeyInput) {
        String featureKey = FeatureKey.parse(featureKeyInput).toKey();
        if (!canAssemble(featureKey)) {
            return Optional.empty();
        }
        return Optional.of(assembler.assemble(featureKey));
    }

    private boolean canAssemble(String featureKey) {
        if (hierarchyStore.getByFeatureKey(featureKey).isPresent()) {
            return true;
        }
        if (!caseQueryService.queryForMap(featureKey).getItems().isEmpty()) {
            return true;
        }
        FeatureKey parts = FeatureKey.parse(featureKey);
        return !apiMapCatalog.findByFeatureName(parts.feature()).isEmpty();
    }
}
