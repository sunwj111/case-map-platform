package com.casemap.hierarchy.web;

import com.casemap.hierarchy.store.HierarchyStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final HierarchyStore store;

    public HealthController(HierarchyStore store) {
        this.store = store;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "ok", true,
                "service", "hierarchy",
                "stack", "java-spring-boot",
                "nodes", store.size()
        );
    }
}
