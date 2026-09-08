package com.casemap.hierarchy.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteKnowledgeCatalogTest {

    @Test
    void matchesQuantityPriceRulesAndAmountTables() throws Exception {
        QuoteKnowledgeCatalog catalog = new QuoteKnowledgeCatalog(new ObjectMapper());
        List<QuoteRule> rules = catalog.findRules("金额计算与汇总", "数量价汇总", List.of("金额计算与汇总"));

        assertTrue(rules.stream().anyMatch(rule -> "R002".equals(rule.getId())));
        assertTrue(rules.stream().anyMatch(rule -> "R014".equals(rule.getId())));
        assertTrue(rules.stream().noneMatch(rule -> "R001".equals(rule.getId())));

        List<String> tables = catalog.findTables("金额计算与汇总", List.of("金额计算与汇总"));
        assertTrue(tables.contains("quotations_record"));
        assertTrue(tables.contains("offer_rule"));
        assertEquals("quotations_record", QuoteKnowledgeCatalog.toTableName("QuotationsRecord"));
    }
}
