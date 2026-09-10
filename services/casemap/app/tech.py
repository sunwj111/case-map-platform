from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from app.feature_key import parse

RELATION_RANK = {"主接口": 0, "校验接口": 1, "查询接口": 2, "配套接口": 3}


def _trim_to_null(value: str | None) -> str | None:
    if value is None:
        return None
    trimmed = value.strip()
    return None if not trimmed else trimmed


def hits_feature(item: dict[str, Any], feature: str) -> bool:
    for mapped in item.get("features") or []:
        if mapped is None:
            continue
        if mapped == feature or feature in mapped or mapped in feature:
            return True
    return False


def hits_node(item: dict[str, Any], scene: str | None) -> bool:
    if not scene:
        return False
    primary = item.get("primaryNode")
    if scene == primary or (primary and (scene in primary or primary in scene)):
        return True
    return scene in (item.get("nodes") or [])


class FeatureApiMapCatalog:
    def __init__(self, data_file: Path) -> None:
        self.file = json.loads(data_file.read_text(encoding="utf-8"))

    def list_items(self) -> list[dict[str, Any]]:
        return list(self.file.get("items") or [])

    def find_by_feature_name(self, feature_name: str | None) -> list[dict[str, Any]]:
        feature = "" if feature_name is None else feature_name.strip()
        if not feature:
            return []
        return [item for item in self.list_items() if hits_feature(item, feature)]


class FlowNodeCatalog:
    def __init__(self, data_file: Path) -> None:
        self.file = json.loads(data_file.read_text(encoding="utf-8"))

    def list_all(self) -> list[dict[str, Any]]:
        return list(self.file.get("flowNodes") or [])

    def get_source(self) -> str | None:
        return self.file.get("source")

    def find_by_name(self, node_name: str | None) -> dict[str, Any] | None:
        if not node_name:
            return None
        for node in self.list_all():
            if node.get("node") == node_name:
                return node
        return None


class ApiResolveService:
    def __init__(self, api_map: FeatureApiMapCatalog, flow_nodes: FlowNodeCatalog) -> None:
        self.api_map = api_map
        self.flow_nodes = flow_nodes

    def list_flow_nodes(self) -> list[dict[str, Any]]:
        return self.flow_nodes.list_all()

    def resolve(self, feature_key: str | None, feature: str | None, scene: str | None) -> dict[str, Any]:
        resolved_feature = _trim_to_null(feature)
        resolved_scene = _trim_to_null(scene)
        if _trim_to_null(feature_key) is not None:
            parsed = parse(feature_key)
            if resolved_feature is None:
                resolved_feature = parsed.feature
            if resolved_scene is None:
                resolved_scene = parsed.scene
        if resolved_feature is None:
            raise ValueError("请提供 feature 或 featureKey")

        by_feature = []
        by_node = []
        for item in self.api_map.list_items():
            if hits_feature(item, resolved_feature):
                by_feature.append(item)
            elif hits_node(item, resolved_scene):
                by_node.append(item)
        selected = by_feature or by_node
        selected.sort(key=lambda item: _relation_rank(item.get("relation")))
        map_file = self.api_map.file
        result: dict[str, Any] = {
            "feature": resolved_feature,
            "scene": resolved_scene,
            "app": map_file.get("app"),
            "service": map_file.get("service"),
            "source": map_file.get("source"),
            "fallback": False,
            "apis": [],
            "flowNodes": [],
        }
        if not selected:
            result["fallback"] = True
            result["apis"] = [_fallback_api(resolved_feature)]
            result["flowNodes"] = self._merge_flow_nodes([], resolved_scene)
            return result
        apis = []
        for item in selected:
            apis.extend(_expand(item, False))
        result["apis"] = apis
        result["flowNodes"] = self._merge_flow_nodes(selected, resolved_scene)
        return result

    def _merge_flow_nodes(self, selected: list[dict[str, Any]], scene: str | None) -> list[dict[str, Any]]:
        merged: dict[str, dict[str, Any]] = {}
        for item in selected:
            names = []
            if item.get("primaryNode"):
                names.append(item["primaryNode"])
            names.extend(item.get("nodes") or [])
            for node_name in names:
                self._put_flow_node(merged, node_name, scene)
        if scene:
            self._put_flow_node(merged, scene, scene)
        return list(merged.values())

    def _put_flow_node(self, merged: dict[str, dict[str, Any]], node_name: str | None, scene: str | None) -> None:
        if not node_name or node_name in merged:
            return
        catalog_node = self.flow_nodes.find_by_name(node_name)
        resolved = {
            "name": node_name,
            "primary": node_name == scene,
            "risk": None,
            "description": None,
            "source": "feature_api_map",
        }
        if catalog_node:
            resolved["risk"] = catalog_node.get("risk")
            resolved["description"] = catalog_node.get("description")
            resolved["source"] = self.flow_nodes.get_source()
        merged[node_name] = resolved


def _expand(item: dict[str, Any], fallback: bool) -> list[dict[str, Any]]:
    paths = item.get("paths") or [""]
    method = item.get("method") or "POST"
    if not str(method).strip():
        method = "POST"
    rows = []
    for path in paths:
        rows.append({
            "method": method,
            "path": path,
            "label": (method + " " + path).strip(),
            "relation": item.get("relation"),
            "controller": item.get("controller"),
            "primaryNode": item.get("primaryNode"),
            "fallback": fallback,
        })
    return rows


def _fallback_api(feature: str) -> dict[str, Any]:
    path = guess_path(feature)
    return {
        "method": "POST",
        "path": path,
        "label": "POST " + path,
        "relation": "回退接口",
        "controller": None,
        "primaryNode": None,
        "fallback": True,
    }


def guess_path(feature: str | None) -> str:
    text = feature or ""
    if _contains_any(text, "数量价", "固定价", "租赁价", "去利润", "分组", "金额", "汇总", "报价单生成", "价格来源", "场景", "类型识别"):
        return "/quotation/offer"
    if _contains_any(text, "参数", "精度", "必填"):
        return "/quotation/checkParamBeforeSubmit"
    if _contains_any(text, "物料", "带出", "配件"):
        return "/quotation/bim/offer"
    if _contains_any(text, "造价", "明细落库"):
        return "/quotation/createDesignCost"
    if _contains_any(text, "提交审核", "自动审核", "审核拒绝"):
        return "/quotation/submitDesignCost"
    if _contains_any(text, "库存", "失效", "预占"):
        return "/quotation/confirmDesignCost"
    return "/quotation/offer"


def _contains_any(text: str, *keywords: str) -> bool:
    return any(keyword in text for keyword in keywords)


def _relation_rank(relation: str | None) -> int:
    if relation is None:
        return 9
    for key, rank in RELATION_RANK.items():
        if relation.startswith(key):
            return rank
    return 9
