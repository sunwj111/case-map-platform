from __future__ import annotations

from typing import Any


def trimmed_text(value: Any) -> str:
    return "" if value is None else str(value).strip()


def pick_label(primary: Any, alias: Any, field_name: str) -> str:
    left = trimmed_text(primary)
    right = trimmed_text(alias)
    if left and right and left != right:
        raise ValueError(field_name + "与平台字段不一致")
    chosen = left or right
    if "/" in chosen or "\n" in chosen or "\r" in chosen:
        raise ValueError(field_name + "不能包含斜杠或换行")
    return chosen


def resolve_project_context(body: dict[str, Any] | None) -> dict[str, str]:
    payload = body or {}
    domain = pick_label(payload.get("domain"), payload.get("ziroom_domain"), "领域")
    system = pick_label(payload.get("system"), payload.get("system_ref"), "系统")
    module_name = pick_label(payload.get("moduleName"), payload.get("module_name"), "模块")
    process_node = pick_label(payload.get("processNode"), payload.get("process_node"), "流程节点")
    if not domain:
        raise ValueError("领域不能为空")
    if not system:
        raise ValueError("系统不能为空")
    return {
        "domain": domain,
        "system": system,
        "moduleName": module_name,
        "processNode": process_node,
    }
