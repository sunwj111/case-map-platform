package com.casemap.hierarchy.web;

import com.casemap.hierarchy.featurekey.FeatureKey;
import com.casemap.hierarchy.featurekey.FeatureKeyException;
import com.casemap.hierarchy.model.CascadeOptions;
import com.casemap.hierarchy.model.CreateNodeRequest;
import com.casemap.hierarchy.model.FeatureKeyDecodeRequest;
import com.casemap.hierarchy.model.FeatureKeyEncodeRequest;
import com.casemap.hierarchy.model.HierarchyNode;
import com.casemap.hierarchy.model.HierarchyTreeNode;
import com.casemap.hierarchy.model.NodeLevel;
import com.casemap.hierarchy.model.NodeStatus;
import com.casemap.hierarchy.model.UpdateNodeRequest;
import com.casemap.hierarchy.store.HierarchyStore;
import com.casemap.hierarchy.store.SeedData;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HierarchyController {

    private final HierarchyStore store;

    public HierarchyController(HierarchyStore store) {
        this.store = store;
    }

    @GetMapping("/feature-keys/examples")
    public Map<String, Object> examples() {
        List<Map<String, Object>> examples = new ArrayList<>();
        for (String key : FeatureKey.exampleQuoteKeys()) {
            FeatureKey parts = FeatureKey.parse(key);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("featureKey", key);
            item.put("parts", parts.asList());
            item.put("urlEncoded", FeatureKey.encodeForUrl(key));
            examples.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rule", "领域/系统/场景/功能点");
        body.put("separator", "/");
        body.put("examples", examples);
        return body;
    }

    @PostMapping("/feature-keys/encode")
    public Map<String, String> encode(@Valid @RequestBody FeatureKeyEncodeRequest body) {
        String key = FeatureKey.join(body.getDomain(), body.getSystem(), body.getScene(), body.getFeature());
        return Map.of(
                "featureKey", key,
                "urlEncoded", FeatureKey.encodeForUrl(key)
        );
    }

    @PostMapping("/feature-keys/decode")
    public Map<String, String> decode(@RequestBody FeatureKeyDecodeRequest body) {
        String key;
        if (body.getEncoded() != null && !body.getEncoded().isBlank()) {
            key = FeatureKey.decodeFromUrl(body.getEncoded());
        } else if (body.getFeatureKey() != null && !body.getFeatureKey().isBlank()) {
            key = FeatureKey.parse(body.getFeatureKey()).toKey();
        } else {
            throw new FeatureKeyException("请提供 featureKey 或 encoded");
        }
        FeatureKey parts = FeatureKey.parse(key);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("featureKey", key);
        result.put("domain", parts.domain());
        result.put("system", parts.system());
        result.put("scene", parts.scene());
        result.put("feature", parts.feature());
        return result;
    }

    @GetMapping("/hierarchy/tree")
    public List<HierarchyTreeNode> tree(@RequestParam(defaultValue = "false") boolean includeDisabled) {
        return store.asTree(includeDisabled);
    }

    @GetMapping("/hierarchy/nodes")
    public List<HierarchyNode> nodes(
            @RequestParam(required = false) NodeLevel level,
            @RequestParam(required = false) String parentId,
            @RequestParam(required = false) NodeStatus status,
            @RequestParam(defaultValue = "false") boolean includeDisabled
    ) {
        return store.listNodes(level, parentId, status, includeDisabled);
    }

    @GetMapping("/hierarchy/cascade")
    public CascadeOptions cascade(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String system,
            @RequestParam(required = false) String scene
    ) {
        return store.cascadeOptions(domain, system, scene);
    }

    @GetMapping("/hierarchy/features/{*featureKey}")
    public HierarchyNode feature(@PathVariable("featureKey") String featureKey) {
        String key = FeatureKey.fromPathVariable(featureKey);
        return store.getByFeatureKey(key)
                .orElseThrow(() -> new NotFoundException("功能点不存在：" + key));
    }

    @PostMapping("/hierarchy/nodes")
    @ResponseStatus(HttpStatus.CREATED)
    public HierarchyNode create(@Valid @RequestBody CreateNodeRequest body) {
        return store.create(body);
    }

    @PatchMapping("/hierarchy/nodes/{nodeId}")
    public HierarchyNode update(@PathVariable String nodeId, @RequestBody UpdateNodeRequest body) {
        try {
            return store.update(nodeId, body);
        } catch (IllegalArgumentException ex) {
            if ("节点不存在".equals(ex.getMessage())) {
                throw new NotFoundException(ex.getMessage());
            }
            throw ex;
        }
    }

    @PostMapping("/hierarchy/nodes/{nodeId}/disable")
    public HierarchyNode disable(@PathVariable String nodeId) {
        try {
            return store.disable(nodeId);
        } catch (IllegalArgumentException ex) {
            if ("节点不存在".equals(ex.getMessage())) {
                throw new NotFoundException(ex.getMessage());
            }
            throw ex;
        }
    }

    @PostMapping("/hierarchy/seed/reset")
    public Map<String, Object> resetSeed() {
        store.replaceAll(SeedData.quoteNodes());
        return Map.of("ok", true, "count", store.size());
    }
}
