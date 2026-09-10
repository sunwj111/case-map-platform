from __future__ import annotations

import json
import xml.etree.ElementTree as ElementTree
import zipfile
from io import BytesIO
from typing import Any

MAX_CONTENT_BYTES = 10 * 1024 * 1024
MAX_NODES = 10000
MAX_DEPTH = 100


def parse_xmind_archive(content: bytes) -> dict[str, Any]:
    if not content:
        raise ValueError("XMind 文件为空")
    content_json = None
    content_xml = None
    try:
        with zipfile.ZipFile(BytesIO(content)) as archive:
            for info in archive.infolist():
                if info.is_dir():
                    continue
                entry_name = info.filename.replace("\\", "/")
                if entry_name == "content.json":
                    content_json = _read_zip_entry(archive, info)
                elif entry_name == "content.xml":
                    content_xml = _read_zip_entry(archive, info)
    except ValueError:
        raise
    except Exception as error:
        raise ValueError("无法读取 XMind 文件：" + str(error)) from error
    if content_json is not None:
        nodes = _parse_content_json(content_json)
        format_name = "xmind-json"
    elif content_xml is not None:
        nodes = _parse_content_xml(content_xml)
        format_name = "xmind-xml"
    else:
        raise ValueError("XMind 包中未找到 content.json 或 content.xml")
    if not nodes:
        raise ValueError("XMind 中没有可解析的主题")
    return {
        "format": format_name,
        "nodeCount": len(nodes),
        "rows": _nodes_to_case_rows(nodes),
        "knowledgeText": _nodes_to_knowledge_markdown(nodes),
    }


def _read_zip_entry(archive: zipfile.ZipFile, info: zipfile.ZipInfo) -> bytes:
    if info.file_size > MAX_CONTENT_BYTES:
        raise ValueError("XMind 内容超过 10MB 限制")
    payload = archive.read(info)
    if len(payload) > MAX_CONTENT_BYTES:
        raise ValueError("XMind 内容超过 10MB 限制")
    return payload


def _parse_content_json(content: bytes) -> list[dict[str, Any]]:
    try:
        root = json.loads(content.decode("utf-8"))
        sheets = []
        if isinstance(root, list):
            sheets = root
        elif isinstance(root, dict) and isinstance(root.get("sheets"), list):
            sheets = root["sheets"]
        elif isinstance(root, dict) and "rootTopic" in root:
            sheets = [root]
        else:
            raise ValueError("无法识别 XMind content.json 结构")
        nodes: list[dict[str, Any]] = []
        for sheet in sheets:
            root_topic = sheet.get("rootTopic")
            if not isinstance(root_topic, dict):
                continue
            initial_path = []
            sheet_title = _compact(sheet.get("title"))
            if sheet_title:
                initial_path.append(sheet_title)
            _walk_json_topic(root_topic, initial_path, nodes)
        return nodes
    except ValueError:
        raise
    except Exception as error:
        raise ValueError("XMind content.json 解析失败：" + str(error)) from error


def _walk_json_topic(topic: dict[str, Any], parent_path: list[str], nodes: list[dict[str, Any]]) -> None:
    _ensure_node_limit(nodes, len(parent_path))
    title = _json_title(topic)
    current_path = list(parent_path)
    if title:
        current_path.append(title)
    children = _json_children(topic)
    nodes.append({
        "title": title,
        "notes": _json_notes(topic),
        "path": list(current_path),
        "depth": len(current_path),
        "leaf": not children,
    })
    for child in children:
        _walk_json_topic(child, current_path, nodes)


def _json_title(topic: dict[str, Any]) -> str:
    title = topic.get("title")
    if isinstance(title, str):
        return _compact(title)
    if isinstance(title, dict):
        text = title.get("text") or title.get("content") or ""
        return _compact(text)
    return _compact(topic.get("@title"))


def _json_notes(topic: dict[str, Any]) -> str:
    notes = topic.get("notes")
    if isinstance(notes, str):
        return _compact(notes)
    if isinstance(notes, dict):
        plain = notes.get("plain")
        if isinstance(plain, str):
            return _compact(plain)
        if isinstance(plain, dict) and plain.get("content"):
            return _compact(plain.get("content"))
        return _compact(notes.get("content"))
    return ""


def _json_children(topic: dict[str, Any]) -> list[dict[str, Any]]:
    children_node = topic.get("children") or {}
    if not isinstance(children_node, dict):
        return []
    children = []
    for group_name in ("attached", "detached", "summary"):
        group = children_node.get(group_name) or []
        if isinstance(group, list):
            children.extend(item for item in group if isinstance(item, dict))
    return children


def _parse_content_xml(content: bytes) -> list[dict[str, Any]]:
    try:
        root = ElementTree.fromstring(content)
    except Exception as error:
        raise ValueError("XMind content.xml 解析失败：" + str(error)) from error
    parent_by_element = _xml_parent_map(root)
    nodes: list[dict[str, Any]] = []
    for topic in _root_topics(root, parent_by_element):
        _walk_xml_topic(topic, [], nodes)
    return nodes


def _xml_parent_map(root: ElementTree.Element) -> dict[ElementTree.Element, ElementTree.Element]:
    mapping: dict[ElementTree.Element, ElementTree.Element] = {}
    for parent in root.iter():
        for child in list(parent):
            mapping[child] = parent
    return mapping


def _root_topics(
    root: ElementTree.Element,
    parent_by_element: dict[ElementTree.Element, ElementTree.Element],
) -> list[ElementTree.Element]:
    topics = [element for element in root.iter() if _local_name(element.tag) == "topic"]
    result = []
    for topic in topics:
        if not _has_topic_ancestor(topic, parent_by_element):
            result.append(topic)
    return result


def _has_topic_ancestor(
    topic: ElementTree.Element,
    parent_by_element: dict[ElementTree.Element, ElementTree.Element],
) -> bool:
    parent = parent_by_element.get(topic)
    while parent is not None:
        if _local_name(parent.tag) == "topic":
            return True
        parent = parent_by_element.get(parent)
    return False


def _walk_xml_topic(topic: ElementTree.Element, parent_path: list[str], nodes: list[dict[str, Any]]) -> None:
    _ensure_node_limit(nodes, len(parent_path))
    title = _xml_title(topic)
    current_path = list(parent_path)
    if title:
        current_path.append(title)
    children = _xml_children(topic)
    nodes.append({
        "title": title,
        "notes": _xml_notes(topic),
        "path": list(current_path),
        "depth": len(current_path),
        "leaf": not children,
    })
    for child in children:
        _walk_xml_topic(child, current_path, nodes)


def _xml_children(topic: ElementTree.Element) -> list[ElementTree.Element]:
    children = []
    for child in list(topic):
        if _local_name(child.tag) != "children":
            continue
        for topics_element in list(child):
            if _local_name(topics_element.tag) != "topics":
                continue
            for child_topic in list(topics_element):
                if _local_name(child_topic.tag) == "topic":
                    children.append(child_topic)
    return children


def _xml_title(topic: ElementTree.Element) -> str:
    title_attribute = _compact(topic.attrib.get("title"))
    if title_attribute:
        return title_attribute
    for child in list(topic):
        if _local_name(child.tag) == "title":
            return _compact("".join(child.itertext()))
    return ""


def _xml_notes(topic: ElementTree.Element) -> str:
    for child in list(topic):
        if _local_name(child.tag) == "notes":
            return _compact("".join(child.itertext()))
    return ""


def _local_name(tag: str) -> str:
    if "}" in tag:
        return tag.rsplit("}", 1)[-1].lower()
    return tag.split(":")[-1].lower()


def _ensure_node_limit(nodes: list[dict[str, Any]], depth: int) -> None:
    if len(nodes) >= MAX_NODES:
        raise ValueError("XMind 主题数超过限制")
    if depth > MAX_DEPTH:
        raise ValueError("XMind 层级超过限制")


def _nodes_to_case_rows(nodes: list[dict[str, Any]]) -> list[dict[str, Any]]:
    candidates = [
        node for node in nodes
        if node.get("leaf") and node.get("title") and node.get("depth", 0) >= 2
    ]
    if not candidates:
        candidates = [node for node in nodes if node.get("title") and node.get("depth", 0) >= 2]
    rows = []
    for index, node in enumerate(candidates):
        path = node.get("path") or []
        feature = path[-2] if len(path) >= 2 else ""
        scene = path[-3] if len(path) >= 3 else (path[0] if path else "")
        step, expected = _split_notes(node.get("notes") or "")
        rows.append({
            "originalCaseId": "XMIND-ROW-" + str(index + 1),
            "caseName": node.get("title"),
            "step": step,
            "expected": expected,
            "scene": scene,
            "feature": feature,
            "module": path[0] if path else "",
            "sourceRowNumber": index + 1,
        })
    return rows


def _nodes_to_knowledge_markdown(nodes: list[dict[str, Any]]) -> str:
    features_by_scene: dict[str, list[str]] = {}
    rules_by_scene: dict[str, list[str]] = {}
    for node in nodes:
        title = node.get("title") or ""
        path = node.get("path") or []
        if not title or node.get("depth", 0) < 2 or not path:
            continue
        scene = path[0]
        features_by_scene.setdefault(scene, [])
        rules_by_scene.setdefault(scene, [])
        if _is_rule(title):
            if title not in rules_by_scene[scene]:
                rules_by_scene[scene].append(title)
        elif title not in features_by_scene[scene]:
            features_by_scene[scene].append(title)
    lines = ["# 从 XMind 导入的知识草稿", ""]
    for scene, features in features_by_scene.items():
        lines.append("## 场景：" + scene)
        for feature in features:
            lines.append("- 功能点：" + feature)
        for rule in rules_by_scene.get(scene) or []:
            lines.append("- 规则：" + rule)
        lines.append("")
    return "\n".join(lines).strip()


def _split_notes(notes: str) -> tuple[str, str]:
    text = _compact(notes)
    if not text:
        return "", ""
    for separator in ("预期", "期望", "Expected"):
        if separator in text:
            left, right = text.split(separator, 1)
            return _compact(left), _compact(right)
    return text, ""


def _is_rule(title: str) -> bool:
    return any(token in title for token in ("必须", "不可", "不能", "应当", "禁止", "规则", "校验"))


def _compact(value: Any) -> str:
    if value is None:
        return ""
    return " ".join(str(value).split())
