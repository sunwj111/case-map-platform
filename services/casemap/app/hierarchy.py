from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from threading import Lock
from typing import Any
from uuid import uuid4

from app.errors import NotFoundError
from app.feature_key import join, parse

LEVELS = ("domain", "system", "scene", "feature")
PARENT_LEVEL = {"system": "domain", "scene": "system", "feature": "scene"}


def _now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def quote_seed_nodes() -> list[dict[str, Any]]:
    current_time = _now()
    nodes: list[dict[str, Any]] = []
    domain = _node("dom-home", "domain", "家装", None, "home", "家装事业线", 10, ["家装"], current_time)
    system = _node("sys-quote", "system", "报价", domain["id"], "quote", "报价系统", 10, ["家装", "报价"], current_time)
    nodes.extend([domain, system])
    scenes = {
        "金额计算与汇总": ["数量价汇总", "固定价汇总", "去利润价计算", "分组汇总"],
        "造价提交与审核": ["自动审核判定", "提交审核", "审核拒绝回退"],
        "参数解析与输入精度": ["参数必填校验", "输入精度校验"],
    }
    scene_index = 0
    for scene_name, features in scenes.items():
        scene_index += 1
        scene_id = "scene-" + str(scene_index)
        scene = _node(
            scene_id, "scene", scene_name, system["id"], None, "", scene_index * 10,
            ["家装", "报价", scene_name], current_time,
        )
        nodes.append(scene)
        feature_index = 0
        for feature_name in features:
            feature_index += 1
            path_names = ["家装", "报价", scene_name, feature_name]
            feature = _node(
                "feat-" + str(scene_index) + "-" + str(feature_index),
                "feature", feature_name, scene_id, None, "", feature_index * 10,
                path_names, current_time,
            )
            feature["featureKey"] = join(*path_names)
            feature["meta"]["seed"] = True
            nodes.append(feature)
    extras = [
        ("dom-shoufang", "收房", "shoufang", 20),
        ("dom-chufang", "出房", "chufang", 30),
        ("dom-jiafu", "家服", "jiafu", 40),
        ("dom-lingzhi", "灵之", "lingzhi", 50),
        ("dom-qixin", "企信", "qixin", 60),
    ]
    for node_id, name, code, sort_order in extras:
        nodes.append(_node(
            node_id, "domain", name, None, code, name + "领域（待补充系统）",
            sort_order, [name], current_time,
        ))
    return nodes


def _node(
    node_id: str, level: str, name: str, parent_id: str | None, code: str | None,
    description: str, sort_order: int, path_names: list[str], current_time: str,
) -> dict[str, Any]:
    return {
        "id": node_id,
        "level": level,
        "name": name,
        "code": code,
        "parentId": parent_id,
        "status": "enabled",
        "description": description,
        "sortOrder": sort_order,
        "featureKey": None,
        "pathNames": list(path_names),
        "meta": {},
        "createdAt": current_time,
        "updatedAt": current_time,
    }


class HierarchyStore:
    def __init__(self, data_file: Path) -> None:
        self.data_file = data_file
        self._lock = Lock()
        self.nodes: dict[str, dict[str, Any]] = {}
        if data_file.exists():
            self._load()
        if not self.nodes:
            self.replace_all(quote_seed_nodes())

    def size(self) -> int:
        return len(self.nodes)

    def list_nodes(
        self,
        level: str | None = None,
        parent_id: str | None = None,
        status: str | None = None,
        include_disabled: bool = False,
    ) -> list[dict[str, Any]]:
        rows = []
        for node in self.nodes.values():
            if level and node.get("level") != level:
                continue
            if parent_id is not None and node.get("parentId") != parent_id:
                continue
            if status:
                if node.get("status") != status:
                    continue
            elif not include_disabled and node.get("status") == "disabled":
                continue
            rows.append(node)
        rows.sort(key=lambda item: (item.get("sortOrder") or 0, item.get("name") or ""))
        return rows

    def get(self, node_id: str) -> dict[str, Any] | None:
        return self.nodes.get(node_id)

    def get_by_feature_key(self, feature_key: str) -> dict[str, Any] | None:
        key = parse(feature_key).to_key()
        for node in self.nodes.values():
            if node.get("level") == "feature" and node.get("featureKey") == key:
                return node
        return None

    def replace_all(self, seed: list[dict[str, Any]]) -> None:
        with self._lock:
            self.nodes = {node["id"]: node for node in seed}
            self._save()

    def create(self, body: dict[str, Any]) -> dict[str, Any]:
        name = self._normalize_name(body.get("name"))
        level = body.get("level")
        parent_id = body.get("parentId")
        path_names = self._path_names_for_create(level, name, parent_id)
        with self._lock:
            for existing in self.nodes.values():
                if (
                    existing.get("level") == level
                    and existing.get("parentId") == parent_id
                    and existing.get("name") == name
                    and existing.get("status") != "disabled"
                ):
                    raise ValueError("同级已存在「" + name + "」")
            node = {
                "id": str(uuid4()),
                "level": level,
                "name": name,
                "code": body.get("code"),
                "parentId": parent_id,
                "status": body.get("status") or "enabled",
                "description": body.get("description") or "",
                "sortOrder": int(body.get("sortOrder") or 0),
                "pathNames": path_names,
                "meta": dict(body.get("meta") or {}),
                "featureKey": join(*path_names) if level == "feature" else None,
                "createdAt": _now(),
                "updatedAt": _now(),
            }
            self.nodes[node["id"]] = node
            self._save()
            return node

    def update(self, node_id: str, body: dict[str, Any]) -> dict[str, Any]:
        with self._lock:
            node = self.nodes.get(node_id)
            if node is None:
                raise NotFoundError("节点不存在")
            rename = body.get("name") is not None and body.get("name") != node.get("name")
            if body.get("name") is not None:
                node["name"] = self._normalize_name(body.get("name"))
            for field_name in ("code", "description", "status", "sortOrder", "meta"):
                if field_name in body and body[field_name] is not None:
                    node[field_name] = body[field_name]
            node["updatedAt"] = _now()
            if rename:
                self._rename_cascade(node)
            self.nodes[node_id] = node
            self._save()
            return node

    def disable(self, node_id: str) -> dict[str, Any]:
        return self.update(node_id, {"status": "disabled"})

    def as_tree(self, include_disabled: bool) -> list[dict[str, Any]]:
        all_nodes = self.list_nodes(include_disabled=include_disabled)
        by_parent: dict[str | None, list[dict[str, Any]]] = {}
        for node in all_nodes:
            by_parent.setdefault(node.get("parentId"), []).append(node)
        return self._build_tree(None, by_parent)

    def cascade_options(self, domain: str | None, system: str | None, scene: str | None) -> dict[str, Any]:
        options: dict[str, Any] = {
            "domains": [node["name"] for node in self.list_nodes("domain")],
            "systems": [],
            "scenes": [],
            "features": [],
        }
        domain_node = self._find_by_name("domain", None, domain)
        if domain_node:
            options["systems"] = [node["name"] for node in self.list_nodes("system", domain_node["id"])]
        system_node = None if domain_node is None else self._find_by_name("system", domain_node["id"], system)
        if system_node:
            options["scenes"] = [node["name"] for node in self.list_nodes("scene", system_node["id"])]
        scene_node = None if system_node is None else self._find_by_name("scene", system_node["id"], scene)
        if scene_node:
            options["features"] = [
                {"name": node["name"], "featureKey": node.get("featureKey") or ""}
                for node in self.list_nodes("feature", scene_node["id"])
            ]
        return options

    def _find_by_name(self, level: str, parent_id: str | None, name: str | None) -> dict[str, Any] | None:
        if not name:
            return None
        for node in self.list_nodes(level, parent_id):
            if node.get("name") == name:
                return node
        return None

    def _build_tree(self, parent_id: str | None, by_parent: dict[str | None, list[dict[str, Any]]]) -> list[dict[str, Any]]:
        children = by_parent.get(parent_id, [])
        result = []
        for node in children:
            result.append({
                "id": node["id"],
                "level": node["level"],
                "name": node["name"],
                "status": node["status"],
                "featureKey": node.get("featureKey"),
                "children": self._build_tree(node["id"], by_parent),
            })
        return result

    def _path_names_for_create(self, level: str, name: str, parent_id: str | None) -> list[str]:
        if level == "domain":
            return [name]
        if not parent_id:
            raise ValueError(str(level) + " 必须指定 parentId")
        parent = self.nodes.get(parent_id)
        if parent is None:
            raise ValueError("父节点不存在")
        expected = PARENT_LEVEL.get(level)
        if parent.get("level") != expected:
            raise ValueError(str(level) + " 的父级必须是 " + str(expected))
        return list(parent.get("pathNames") or []) + [name]

    def _rename_cascade(self, renamed: dict[str, Any]) -> None:
        index = LEVELS.index(renamed["level"])
        new_path = list(renamed.get("pathNames") or [])
        if len(new_path) <= index:
            new_path = self._path_names_for_create(renamed["level"], renamed["name"], renamed.get("parentId"))
        else:
            new_path[index] = renamed["name"]
        renamed["pathNames"] = new_path
        if renamed["level"] == "feature":
            renamed["featureKey"] = join(*new_path)
        self._walk_rename(renamed["id"], renamed["pathNames"])

    def _walk_rename(self, parent_id: str, parent_path: list[str]) -> None:
        for child in list(self.nodes.values()):
            if child.get("parentId") != parent_id:
                continue
            child_path = list(parent_path) + [child["name"]]
            child["pathNames"] = child_path
            if child.get("level") == "feature":
                child["featureKey"] = join(*child_path)
            child["updatedAt"] = _now()
            self.nodes[child["id"]] = child
            self._walk_rename(child["id"], child_path)

    def _normalize_name(self, name: str | None) -> str:
        value = "" if name is None else name.strip()
        if not value or "/" in value:
            raise ValueError("name 非法")
        return value

    def _load(self) -> None:
        payload = json.loads(self.data_file.read_text(encoding="utf-8"))
        loaded = payload.get("nodes") or []
        self.nodes = {node["id"]: node for node in loaded}

    def _save(self) -> None:
        self.data_file.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "version": 1,
            "updatedAt": _now(),
            "nodes": self.list_nodes(include_disabled=True),
        }
        temporary = self.data_file.with_suffix(self.data_file.suffix + ".tmp")
        temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        temporary.replace(self.data_file)
