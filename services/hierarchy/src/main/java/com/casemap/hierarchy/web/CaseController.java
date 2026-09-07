package com.casemap.hierarchy.web;

import com.casemap.hierarchy.asset.CaseQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final CaseQueryService caseQueryService;

    public CaseController(CaseQueryService caseQueryService) {
        this.caseQueryService = caseQueryService;
    }

    @GetMapping
    public CaseQueryService.CaseQueryResult list(
            @RequestParam(required = false) String featureKey,
            @RequestParam(required = false) String feature,
            @RequestParam(required = false) String scene
    ) {
        return caseQueryService.query(featureKey, feature, scene);
    }
}
