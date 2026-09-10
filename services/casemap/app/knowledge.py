from __future__ import annotations

import json
from pathlib import Path
from typing import Any


class QuoteKnowledgeCatalog:
    def __init__(self, data_file: Path) -> None:
        self.file = json.loads(data_file.read_text(encoding="utf-8"))

    def get_source(self) -> str:
        source = self.file.get("source")
        return source if source else "configs/quote.json"

    def list_rules(self) -> list[dict[str, Any]]:
        return list(self.file.get("rules") or [])

    def find_rules(self, scene: str | None, feature: str | None, flow_node_names: list[str] | None) -> list[dict[str, Any]]:
        node_matched = [
            rule for rule in self.list_rules()
            if matches_node(rule.get("node"), scene, flow_node_names)
        ]
        feature_matched = [rule for rule in node_matched if matches_feature(rule, feature)]
        if not feature_matched:
            return node_matched
        for rule in node_matched:
            if rule in feature_matched:
                continue
            if rule.get("testType") == "边界值":
                feature_matched.append(rule)
        return feature_matched

    def find_tables(self, scene: str | None, flow_node_names: list[str] | None) -> list[str]:
        tables: list[str] = []
        seen: set[str] = set()
        for field in self.file.get("fields") or []:
            if not matches_node(field.get("node"), scene, flow_node_names):
                continue
            table_name = to_table_name(field.get("entity"))
            if table_name and table_name not in seen:
                seen.add(table_name)
                tables.append(table_name)
        return tables


def matches_node(node_name: str | None, scene: str | None, flow_node_names: list[str] | None) -> bool:
    if not node_name:
        return False
    if scene and (node_name == scene or scene in node_name or node_name in scene):
        return True
    return bool(flow_node_names and node_name in flow_node_names)


def matches_feature(rule: dict[str, Any], feature: str | None) -> bool:
    if not feature:
        return False
    rule_name = (rule.get("name") or "").lower()
    haystack = (rule_name + " " + (rule.get("description") or "")).lower()
    needle = feature.lower()
    if needle in haystack or (rule_name and rule_name in needle):
        return True
    for token in feature_tokens(feature):
        if token.lower() in haystack:
            return True
    return False


def feature_tokens(feature: str) -> list[str]:
    remaining = feature
    for splitter in ("与", "和", "及"):
        remaining = remaining.replace(splitter, " ")
    tokens = [part for part in remaining.split() if len(part) >= 2]
    for keyword in ("数量价", "固定价", "去利润", "分组", "审核"):
        if keyword in feature:
            tokens.append(keyword)
    if "必填" in feature or "参数" in feature:
        tokens.extend(["参数", "必填"])
    return tokens


def to_table_name(entity: str | None) -> str | None:
    if not entity:
        return None
    table_name = []
    for index, character in enumerate(entity):
        if character.isupper() and table_name:
            table_name.append("_")
        table_name.append(character.lower())
    return "".join(table_name)
