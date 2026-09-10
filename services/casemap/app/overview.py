from __future__ import annotations

from typing import Any

from app.assets import CaseAssetStore, is_published_official
from app.feature_key import parse
from app.hierarchy import HierarchyStore

UNNAMED_MODULE_LABEL = "未填写模块"


class OverviewTreeService:
    def __init__(self, hierarchy_store: HierarchyStore, case_store: CaseAssetStore) -> None:
        self.hierarchy_store = hierarchy_store
        self.case_store = case_store

    def as_tree(self) -> dict[str, Any]:
        grouped = self._group_published_cases()
        domain_names = self._ordered_names(
            [node["name"] for node in self.hierarchy_store.list_nodes("domain")],
            grouped.keys(),
        )
        tree = [self._domain_node(domain_name, grouped) for domain_name in domain_names]
        return {
            "axis": ["domain", "system", "module"],
            "unnamedModuleLabel": UNNAMED_MODULE_LABEL,
            "totalCases": sum(node["caseCount"] for node in tree),
            "nodes": tree,
        }

    def cascade(self, domain: str | None, system: str | None) -> dict[str, Any]:
        tree = self.as_tree()
        domains = [node["name"] for node in tree["nodes"]]
        systems: list[str] = []
        modules: list[dict[str, Any]] = []
        domain_node = _find_named(tree["nodes"], domain)
        if domain_node:
            systems = [node["name"] for node in domain_node["children"]]
            system_node = _find_named(domain_node["children"], system)
            if system_node:
                modules = [
                    {
                        "name": node["name"],
                        "moduleName": node.get("moduleName") or "",
                        "caseCount": node["caseCount"],
                    }
                    for node in system_node["children"]
                ]
        return {
            "domains": domains,
            "systems": systems,
            "modules": modules,
        }

    def _group_published_cases(self) -> dict[str, dict[str, dict[str, dict[str, Any]]]]:
        grouped: dict[str, dict[str, dict[str, dict[str, Any]]]] = {}
        for asset in self.case_store.list_all():
            if not is_published_official(asset):
                continue
            feature_key = asset.get("featureKey")
            try:
                parts = parse(feature_key)
            except ValueError:
                continue
            module_name = (asset.get("moduleName") or "").strip()
            domain_bucket = grouped.setdefault(parts.domain, {})
            system_bucket = domain_bucket.setdefault(parts.system, {})
            module_bucket = system_bucket.setdefault(module_name, {"scenes": {}})
            scene_bucket = module_bucket["scenes"].setdefault(parts.scene, {"features": {}})
            feature_bucket = scene_bucket["features"].setdefault(
                parts.feature,
                {"featureKey": parts.to_key(), "cases": []},
            )
            feature_bucket["cases"].append(asset)
        return grouped

    def _domain_node(
        self,
        domain_name: str,
        grouped: dict[str, dict[str, dict[str, dict[str, Any]]]],
    ) -> dict[str, Any]:
        domain_id = next(
            (node["id"] for node in self.hierarchy_store.list_nodes("domain") if node["name"] == domain_name),
            None,
        )
        catalog_systems = (
            [node["name"] for node in self.hierarchy_store.list_nodes("system", domain_id)]
            if domain_id
            else []
        )
        system_names = self._ordered_names(
            catalog_systems,
            (grouped.get(domain_name) or {}).keys(),
        )
        children = [
            self._system_node(domain_name, system_name, grouped)
            for system_name in system_names
        ]
        return _overview_node(
            "domain",
            domain_name,
            children,
            node_id="overview:domain:" + domain_name,
        )

    def _system_node(
        self,
        domain_name: str,
        system_name: str,
        grouped: dict[str, dict[str, dict[str, dict[str, Any]]]],
    ) -> dict[str, Any]:
        modules = (grouped.get(domain_name) or {}).get(system_name) or {}
        module_keys = sorted(key for key in modules if key)
        if "" in modules:
            module_keys.append("")
        children = [
            self._module_node(domain_name, system_name, module_name, modules[module_name])
            for module_name in module_keys
        ]
        return _overview_node(
            "system",
            system_name,
            children,
            node_id="overview:system:" + domain_name + "/" + system_name,
        )

    def _module_node(
        self,
        domain_name: str,
        system_name: str,
        module_name: str,
        module_bucket: dict[str, Any],
    ) -> dict[str, Any]:
        display_name = module_name or UNNAMED_MODULE_LABEL
        scene_names = sorted(module_bucket["scenes"].keys())
        children = [
            self._scene_node(
                domain_name,
                system_name,
                module_name,
                scene_name,
                module_bucket["scenes"][scene_name],
            )
            for scene_name in scene_names
        ]
        return _overview_node(
            "module",
            display_name,
            children,
            node_id="overview:module:" + domain_name + "/" + system_name + "/" + (module_name or "_"),
            extra={"moduleName": module_name},
        )

    def _scene_node(
        self,
        domain_name: str,
        system_name: str,
        module_name: str,
        scene_name: str,
        scene_bucket: dict[str, Any],
    ) -> dict[str, Any]:
        feature_names = sorted(scene_bucket["features"].keys())
        children = [
            self._feature_node(scene_bucket["features"][feature_name])
            for feature_name in feature_names
        ]
        return _overview_node(
            "scene",
            scene_name,
            children,
            node_id="overview:scene:" + domain_name + "/" + system_name + "/" + (module_name or "_") + "/" + scene_name,
        )

    def _feature_node(self, feature_bucket: dict[str, Any]) -> dict[str, Any]:
        cases = sorted(
            feature_bucket["cases"],
            key=lambda asset: (asset.get("priority") or "", asset.get("name") or "", asset.get("id") or ""),
        )
        children = [
            {
                "id": asset.get("id"),
                "level": "case",
                "name": asset.get("name"),
                "priority": asset.get("priority"),
                "caseCount": 1,
                "featureKey": feature_bucket["featureKey"],
                "children": [],
            }
            for asset in cases
        ]
        return _overview_node(
            "feature",
            parse(feature_bucket["featureKey"]).feature,
            children,
            node_id="overview:feature:" + feature_bucket["featureKey"],
            extra={"featureKey": feature_bucket["featureKey"]},
        )

    def _ordered_names(self, preferred: list[str], extra) -> list[str]:
        names: list[str] = []
        for name in preferred:
            if name and name not in names:
                names.append(name)
        for name in sorted(extra):
            if name and name not in names:
                names.append(name)
        return names


def _find_named(nodes: list[dict[str, Any]], name: str | None) -> dict[str, Any] | None:
    if not name:
        return None
    for node in nodes:
        if node.get("name") == name:
            return node
    return None


def _overview_node(
    level: str,
    name: str,
    children: list[dict[str, Any]],
    node_id: str,
    extra: dict[str, Any] | None = None,
) -> dict[str, Any]:
    case_count = 0
    for child in children:
        case_count += int(child.get("caseCount") or 0)
    node = {
        "id": node_id,
        "level": level,
        "name": name,
        "caseCount": case_count,
        "children": children,
    }
    if extra:
        node.update(extra)
    return node
