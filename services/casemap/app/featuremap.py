from __future__ import annotations

import re
from datetime import date
from typing import Any

from app.assets import CaseQueryService
from app.feature_key import FeatureKey, java_hex, java_string_hash, join, parse
from app.hierarchy import HierarchyStore
from app.knowledge import QuoteKnowledgeCatalog
from app.tech import ApiResolveService, FeatureApiMapCatalog

MAP_VERSION = "1.0.0"
SYNTHETIC_VERSION = "1.0.0-synthetic"
DEFAULT_BASELINE = "BL-DRAFT"
SOURCE_OFFICIAL_CASES = "official-case-assets"
SOURCE_DERIVED_CASE = "derived-from-official-case"
SOURCE_DERIVED_TECH = "derived-from-tech-mapping"
SOURCE_DERIVED_SCENE = "derived-from-scene"
SOURCE_UNAVAILABLE = "unavailable"
METRIC_SOURCE = "official-case-assets"
DEFECT_SOURCE_UNAVAILABLE = "unavailable"


def slug(text: str) -> str:
    return re.sub(r"[^a-zA-Z0-9\u4e00-\u9fa5]+", "_", text)


def round_rate(count: int, total: int) -> float:
    if total <= 0:
        return 0.0
    return round((count * 1000.0) / total) / 1000.0


def summarize(cases: list[dict[str, Any]], business_view: dict[str, Any] | None) -> dict[str, Any]:
    official_cases = cases or []
    p0 = p1 = p2 = p3 = 0
    for asset in official_cases:
        priority = (asset.get("priority") or "").upper()
        if priority.startswith("P0"):
            p0 += 1
        elif priority.startswith("P1"):
            p1 += 1
        elif priority.startswith("P2"):
            p2 += 1
        elif priority.startswith("P3"):
            p3 += 1
    total = max(len(official_cases), 1)
    with_script = sum(1 for asset in official_cases if asset.get("api"))
    automation = 0.0 if not official_cases else round_rate(with_script, len(official_cases))
    gap_count = 0
    if business_view and business_view.get("scenarios"):
        gap_count = sum(1 for scenario in business_view["scenarios"] if scenario.get("coverageStatus") == "gap")
    if not official_cases:
        coverage = "gap"
    elif gap_count > 0 or automation < 0.7:
        coverage = "partial"
    else:
        coverage = "covered"
    return {
        "linkedCaseCount": len(official_cases),
        "priorityDistribution": {
            "P0": p0, "P1": p1, "P2": p2, "P3": p3,
            "P0Rate": round_rate(p0, total),
            "P1Rate": round_rate(p1, total),
            "P2Rate": round_rate(p2, total),
            "P3Rate": round_rate(p3, total),
        },
        "automationCoverage": automation,
        "defectCount30d": 0,
        "gapCount": gap_count,
        "coverageStatus": coverage,
        "metricSource": METRIC_SOURCE,
        "defectSource": DEFECT_SOURCE_UNAVAILABLE,
    }


def _tag(tag_type: str, label: str, level: str) -> dict[str, str]:
    return {"type": tag_type, "label": label, "level": level}


def build_risk(
    catalog: QuoteKnowledgeCatalog,
    parts: FeatureKey,
    cases: list[dict[str, Any]],
    tech: dict[str, Any] | None,
    spine: dict[str, Any],
    summary: dict[str, Any],
) -> dict[str, Any]:
    flow_names = [node.get("name") for node in (tech or {}).get("flowNodes") or [] if node.get("name")]
    matched = catalog.find_rules(parts.scene, parts.feature, flow_names)
    rules = [{"id": rule.get("id"), "name": rule.get("name"), "source": catalog.get_source()} for rule in matched]
    tags: list[dict[str, str]] = []
    high_risk = any("高" in str(node.get("risk") or "") for node in (tech or {}).get("flowNodes") or [])
    if high_risk:
        tags.append(_tag("risk", "高风险", "high"))
    if any(token in parts.feature for token in ("价", "金额")) or "金额" in parts.scene:
        tags.append(_tag("calc", "金额计算", "high"))
    if "审核" in parts.feature or "审核" in parts.scene:
        tags.append(_tag("audit", "审核", "high"))
    if "核心链路" in (spine.get("valueTags") or []):
        tags.append(_tag("chain", "核心链路", "high"))
    if not cases:
        tags.append(_tag("gap", "无正式用例", "medium"))
    if summary.get("gapCount", 0) > 0:
        tags.append(_tag("gap", "有缺口", "medium"))
    if summary.get("linkedCaseCount", 0) > 0 and summary.get("automationCoverage", 1) < 0.7:
        tags.append(_tag("auto", "自动化不足", "medium"))
    if not rules:
        tags.append(_tag("rule", "规则待校准", "medium"))
    return {"rules": rules, "defects": [], "tags": tags}


class FeatureMapAssembler:
    def __init__(
        self,
        hierarchy_store: HierarchyStore,
        case_query: CaseQueryService,
        api_resolve: ApiResolveService,
        knowledge: QuoteKnowledgeCatalog,
    ) -> None:
        self.hierarchy_store = hierarchy_store
        self.case_query = case_query
        self.api_resolve = api_resolve
        self.knowledge = knowledge

    def assemble(self, feature_key_input: str) -> dict[str, Any]:
        parts = parse(feature_key_input)
        feature_key = parts.to_key()
        data_sources = ["hierarchy"]
        feature_node = self._safe_feature_node(feature_key, data_sources)
        cases = self._safe_cases(feature_key, data_sources)
        tech = self._safe_tech(feature_key, parts, data_sources)
        spine = self._build_spine(parts, feature_node, cases, data_sources)
        business_view = self._build_business_view(parts, cases, tech, data_sources)
        tech_view = self._build_tech_view(parts, tech, data_sources)
        summary = summarize(cases, business_view)
        data_sources.append(METRIC_SOURCE)
        data_sources.append("defects:" + DEFECT_SOURCE_UNAVAILABLE)
        risk_view = build_risk(self.knowledge, parts, cases, tech, spine, summary)
        if not risk_view["rules"]:
            data_sources.append("rules:unmatched")
        else:
            data_sources.append(self.knowledge.get_source())
        return {
            "meta": self._build_meta(feature_key, feature_node, data_sources),
            "spine": spine,
            "businessView": business_view,
            "techView": tech_view,
            "riskView": risk_view,
            "summary": summary,
            "consumers": [
                {"platform": "自动化测试平台", "usage": "按功能点拉取回归包"},
                {"platform": "质量报告", "usage": "覆盖率与缺陷趋势"},
            ],
        }

    def _safe_feature_node(self, feature_key: str, data_sources: list[str]) -> dict[str, Any] | None:
        try:
            node = self.hierarchy_store.get_by_feature_key(feature_key)
            if node is None:
                data_sources.append("hierarchy:missing")
            return node
        except Exception:
            data_sources.append("hierarchy:degraded")
            return None

    def _safe_cases(self, feature_key: str, data_sources: list[str]) -> list[dict[str, Any]]:
        try:
            result = self.case_query.query_for_map(feature_key)
            data_sources.append(result.get("source") or "正式资产库")
            return list(result.get("items") or [])
        except Exception:
            data_sources.append("case-assets:degraded")
            return []

    def _safe_tech(self, feature_key: str, parts: FeatureKey, data_sources: list[str]) -> dict[str, Any]:
        try:
            result = self.api_resolve.resolve(feature_key, parts.feature, parts.scene)
            data_sources.append(result.get("source") or "tech-mapping")
            if result.get("fallback"):
                data_sources.append("tech-mapping:fallback")
            return result
        except Exception:
            data_sources.append("tech-mapping:degraded")
            return {
                "feature": parts.feature, "scene": parts.scene, "fallback": True,
                "source": "degraded", "apis": [], "flowNodes": [],
            }

    def _build_meta(self, feature_key: str, feature_node: dict[str, Any] | None, data_sources: list[str]) -> dict[str, Any]:
        synthetic = feature_node is None
        if synthetic:
            data_sources.insert(0, "synthetic")
        quality_owner = None
        if feature_node and feature_node.get("meta"):
            quality_owner = feature_node["meta"].get("qualityOwner")
        if not quality_owner:
            quality_owner = "未指定"
        unique_sources = list(dict.fromkeys(data_sources))
        return {
            "mapId": "fm_synth_" + slug(feature_key) if synthetic else "fm_" + java_hex(java_string_hash(feature_key)),
            "featureKey": feature_key,
            "version": SYNTHETIC_VERSION if synthetic else MAP_VERSION,
            "baseline": DEFAULT_BASELINE,
            "synthetic": synthetic,
            "updatedAt": feature_node.get("updatedAt") if feature_node else date.today().isoformat(),
            "qualityOwner": quality_owner,
            "dataSources": unique_sources,
        }

    def _build_spine(
        self,
        parts: FeatureKey,
        feature_node: dict[str, Any] | None,
        cases: list[dict[str, Any]],
        data_sources: list[str],
    ) -> dict[str, Any]:
        process_name = "审核流程" if ("审核" in parts.scene or "审核" in parts.feature) else "报价流程"
        return {
            "domain": parts.domain,
            "app": parts.system,
            "moduleName": _module_name_from_cases(cases),
            "sceneName": parts.scene,
            "featureName": parts.feature,
            "processName": process_name,
            "valueTags": self._value_tags(parts, feature_node),
            "neighborFeatures": self._neighbors(parts, feature_node, data_sources),
        }

    def _value_tags(self, parts: FeatureKey, feature_node: dict[str, Any] | None) -> list[str]:
        tags: list[str] = []
        if feature_node and isinstance((feature_node.get("meta") or {}).get("valueTags"), list):
            for item in feature_node["meta"]["valueTags"]:
                if item:
                    tags.append(str(item))
        if "金额" in parts.scene or "价" in parts.feature:
            tags.append("金额计算")
        if "审核" in parts.scene or "审核" in parts.feature:
            tags.append("审核")
        if "造价审核" in parts.scene or "造价审核" in parts.feature or parts.scene == "造价审核":
            tags.append("造价审核")
        tags.append("核心链路")
        return list(dict.fromkeys(tags))

    def _neighbors(self, parts: FeatureKey, feature_node: dict[str, Any] | None, data_sources: list[str]) -> list[dict[str, str]]:
        try:
            parent_id = feature_node.get("parentId") if feature_node else None
            if parent_id is None:
                for node in self.hierarchy_store.list_nodes("scene"):
                    path = node.get("pathNames") or []
                    if node.get("name") == parts.scene and len(path) >= 2 and path[0] == parts.domain and path[1] == parts.system:
                        parent_id = node["id"]
                        break
            if parent_id is None:
                return []
            neighbors = []
            for sibling in self.hierarchy_store.list_nodes("feature", parent_id):
                if sibling.get("status") == "disabled" or sibling.get("name") == parts.feature:
                    continue
                sibling_key = sibling.get("featureKey") or join(parts.domain, parts.system, parts.scene, sibling["name"])
                neighbors.append({"featureKey": sibling_key, "featureName": sibling["name"]})
            return neighbors
        except Exception:
            data_sources.append("neighbors:degraded")
            return []

    def _build_business_view(
        self, parts: FeatureKey, cases: list[dict[str, Any]], tech: dict[str, Any], data_sources: list[str]
    ) -> dict[str, Any]:
        scenarios: dict[str, dict[str, Any]] = {}
        case_items = []
        scripts = []
        for asset in cases:
            scenario_name = asset.get("testScenario") or "未分类场景"
            if not str(scenario_name).strip():
                scenario_name = "未分类场景"
            scenario = scenarios.setdefault(scenario_name, _new_scenario(scenario_name, "covered"))
            scenario["caseCount"] += 1
            case_items.append({
                "id": asset.get("id"),
                "name": asset.get("name"),
                "priority": asset.get("priority"),
                "testScenario": scenario_name,
                "confidence": asset.get("confidence"),
                "mountAdvice": _mount_advice(asset.get("confidence")),
                "api": asset.get("api"),
                "originalCaseId": asset.get("originalCaseId"),
                "step": asset.get("step"),
                "expected": asset.get("expected"),
                "flowNode": asset.get("flowNode"),
                "sourceType": asset.get("sourceType"),
                "source": SOURCE_OFFICIAL_CASES,
            })
            script = _derive_script_from_case(asset, tech)
            if script:
                scripts.append(script)
        if not scenarios:
            gap = _new_scenario(parts.scene, "gap")
            scenarios[gap["name"]] = gap
            data_sources.append("scenarios:gap")
        for scenario in scenarios.values():
            if scenario["caseCount"] == 0:
                scenario["coverageStatus"] = "gap"
            elif scenario["caseCount"] < 2:
                scenario["coverageStatus"] = "partial"
            else:
                scenario["coverageStatus"] = "covered"
        if not scripts:
            scripts = _derive_scripts_from_tech(tech)
            if scripts:
                data_sources.append("scripts:" + SOURCE_DERIVED_TECH)
        else:
            data_sources.append("scripts:" + SOURCE_DERIVED_CASE)
        templates = []
        for scenario in scenarios.values():
            templates.append({
                "id": "DT-" + scenario["id"],
                "name": scenario["name"] + ("测试数据（待补）" if scenario["caseCount"] == 0 else "测试数据"),
                "source": SOURCE_DERIVED_SCENE if scenario["caseCount"] == 0 else SOURCE_DERIVED_CASE,
                "linkedScenarioId": scenario["id"],
            })
        data_sources.append("data-templates:" + (SOURCE_DERIVED_SCENE if not cases else SOURCE_DERIVED_CASE))
        data_sources.append("executions:" + SOURCE_UNAVAILABLE)
        return {
            "scenarios": list(scenarios.values()),
            "cases": case_items,
            "scripts": scripts,
            "dataTemplates": templates,
            "executions": [{
                "id": "EXEC-UNAVAILABLE",
                "label": "执行平台未接入",
                "results": [],
                "lastRunAt": None,
                "source": SOURCE_UNAVAILABLE,
            }],
        }

    def _build_tech_view(self, parts: FeatureKey, tech: dict[str, Any] | None, data_sources: list[str]) -> dict[str, Any]:
        if tech is None:
            data_sources.append("tech-view:unavailable")
            return {"source": "unavailable", "apis": [], "flowNodes": [], "services": [], "tables": [], "messages": [], "codeModules": []}
        flow_nodes = []
        flow_names = []
        for node in tech.get("flowNodes") or []:
            flow_nodes.append({"name": node.get("name"), "risk": node.get("risk"), "primary": bool(node.get("primary"))})
            if node.get("name"):
                flow_names.append(node["name"])
        apis = []
        modules = []
        module_names: set[str] = set()
        for api in tech.get("apis") or []:
            apis.append({
                "method": api.get("method"),
                "path": api.get("path"),
                "label": api.get("label"),
                "relation": api.get("relation"),
                "controller": api.get("controller"),
                "confidence": 40 if api.get("fallback") else 90,
            })
            controller = api.get("controller")
            if controller and controller not in module_names:
                module_names.add(controller)
                modules.append({"name": controller, "type": "controller"})
        tables = self.knowledge.find_tables(parts.scene, flow_names)
        data_sources.append("tables:unavailable" if not tables else "tables:" + self.knowledge.get_source())
        data_sources.append("messages:" + SOURCE_UNAVAILABLE)
        services = []
        if tech.get("service"):
            services.append({"name": tech["service"], "role": "primary"})
        return {
            "source": tech.get("source") or "tech-mapping",
            "apis": apis,
            "flowNodes": flow_nodes,
            "services": services,
            "tables": tables,
            "messages": [],
            "codeModules": modules,
        }


class FeatureMapQueryService:
    def __init__(
        self,
        hierarchy_store: HierarchyStore,
        case_query: CaseQueryService,
        api_map: FeatureApiMapCatalog,
        assembler: FeatureMapAssembler,
    ) -> None:
        self.hierarchy_store = hierarchy_store
        self.case_query = case_query
        self.api_map = api_map
        self.assembler = assembler

    def find_by_feature_key(self, feature_key_input: str) -> dict[str, Any] | None:
        feature_key = parse(feature_key_input).to_key()
        if not self._can_assemble(feature_key):
            return None
        return self.assembler.assemble(feature_key)

    def _can_assemble(self, feature_key: str) -> bool:
        if self.hierarchy_store.get_by_feature_key(feature_key):
            return True
        if self.case_query.query_for_map(feature_key)["items"]:
            return True
        parts = parse(feature_key)
        return bool(self.api_map.find_by_feature_name(parts.feature))


def _new_scenario(name: str, coverage_status: str) -> dict[str, Any]:
    return {"id": "SC-" + slug(name), "name": name, "coverageStatus": coverage_status, "caseCount": 0}


def _mount_advice(confidence: int | None) -> str:
    if confidence is None:
        return "待确认"
    if confidence >= 85:
        return "自动挂载"
    if confidence >= 70:
        return "待抽检"
    return "人工确认"


def _script_from_api_label(script_id: str, api_label: str, linked_case_id: str | None, source: str) -> dict[str, Any]:
    path = api_label
    space = api_label.rfind(" ")
    if space >= 0:
        path = api_label[space + 1:]
    file_name = path.lstrip("/").replace("/", "_") + ".json"
    return {
        "id": script_id,
        "name": file_name,
        "type": "API",
        "status": "derived",
        "linkedCaseId": linked_case_id,
        "source": source,
    }


def _derive_script_from_case(asset: dict[str, Any], tech: dict[str, Any] | None) -> dict[str, Any] | None:
    api_label = asset.get("api")
    source = SOURCE_DERIVED_CASE
    if (not api_label) and tech and tech.get("apis"):
        api_label = tech["apis"][0].get("label")
        source = SOURCE_DERIVED_TECH
    if not api_label:
        return None
    return _script_from_api_label("SCRIPT-" + str(asset.get("id")), api_label, asset.get("id"), source)


def _derive_scripts_from_tech(tech: dict[str, Any] | None) -> list[dict[str, Any]]:
    if not tech or not tech.get("apis"):
        return []
    scripts = []
    seen: set[str] = set()
    index = 1
    for api in tech["apis"]:
        label = api.get("label") or ((api.get("method") or "") + " " + (api.get("path") or "")).strip()
        if label in seen:
            continue
        seen.add(label)
        scripts.append(_script_from_api_label("SCRIPT-TECH-" + str(index), label, None, SOURCE_DERIVED_TECH))
        index += 1
    return scripts


def _module_name_from_cases(cases: list[dict[str, Any]]) -> str:
    names: list[str] = []
    for asset in cases:
        text = (asset.get("moduleName") or "").strip()
        if text and text not in names:
            names.append(text)
    return "、".join(names)
