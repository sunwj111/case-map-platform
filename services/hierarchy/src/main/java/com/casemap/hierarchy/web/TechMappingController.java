package com.casemap.hierarchy.web;

import com.casemap.hierarchy.tech.ApiResolveService;
import com.casemap.hierarchy.tech.QuoteFlowNode;
import com.casemap.hierarchy.tech.TechMappingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tech-mappings")
public class TechMappingController {

    private final ApiResolveService apiResolveService;

    public TechMappingController(ApiResolveService apiResolveService) {
        this.apiResolveService = apiResolveService;
    }

    @GetMapping("/flow-nodes")
    public Map<String, Object> flowNodes() {
        List<QuoteFlowNode> nodes = apiResolveService.listFlowNodes();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", "configs/quote.json");
        body.put("total", nodes.size());
        body.put("flowNodes", nodes);
        return body;
    }

    @GetMapping("/resolve")
    public TechMappingResult resolve(
            @RequestParam(required = false) String featureKey,
            @RequestParam(required = false) String feature,
            @RequestParam(required = false) String scene
    ) {
        return apiResolveService.resolve(featureKey, feature, scene);
    }
}
