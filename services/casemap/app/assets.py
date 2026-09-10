from __future__ import annotations

import json
from pathlib import Path
from threading import Lock
from typing import Any

from app.feature_key import SEPARATOR, parse

STATUS_CONFIRMED = "已确认"
LIFECYCLE_ARCHIVED = "已归档"


def is_published_official(asset: dict[str, Any]) -> bool:
    return asset.get("status") == STATUS_CONFIRMED and asset.get("lifecycle") != LIFECYCLE_ARCHIVED


class CaseAssetStore:
    def __init__(self, seed_file: Path, runtime_file: Path | None) -> None:
        self.runtime_file = runtime_file
        self._lock = Lock()
        self.items: dict[str, dict[str, Any]] = {}
        self.runtime_items: dict[str, dict[str, Any]] = {}
        payload = json.loads(seed_file.read_text(encoding="utf-8"))
        self.seed_source = payload.get("source") or "正式资产库"
        for item in payload.get("items") or []:
            self.items[item["id"]] = item
        if runtime_file is not None and runtime_file.exists():
            runtime_payload = json.loads(runtime_file.read_text(encoding="utf-8"))
            for item in runtime_payload.get("items") or []:
                self.runtime_items[item["id"]] = item
                self.items[item["id"]] = item

    def list_all(self) -> list[dict[str, Any]]:
        return list(self.items.values())

    def get_source(self) -> str:
        if not self.runtime_items:
            return self.seed_source
        return self.seed_source + " + 评审入库"

    def publish(self, assets: list[dict[str, Any]]) -> None:
        if self.runtime_file is None:
            raise RuntimeError("正式资产运行时存储未配置")
        with self._lock:
            for asset in assets:
                self.runtime_items[asset["id"]] = asset
                self.items[asset["id"]] = asset
            self.runtime_file.parent.mkdir(parents=True, exist_ok=True)
            payload = {"source": "评审入库正式资产", "items": list(self.runtime_items.values())}
            temporary = self.runtime_file.with_suffix(self.runtime_file.suffix + ".tmp")
            temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
            temporary.replace(self.runtime_file)


def _trim_to_null(value: str | None) -> str | None:
    if value is None:
        return None
    trimmed = value.strip()
    return None if not trimmed else trimmed


def _matches_feature(asset: dict[str, Any], feature: str | None, feature_key: str | None) -> bool:
    if feature_key is not None and feature_key == asset.get("featureKey"):
        return True
    if feature is None:
        return feature_key is None
    if feature == asset.get("featureName"):
        return True
    asset_key = asset.get("featureKey")
    return asset_key is not None and asset_key.endswith("/" + feature)


def _matches_scene(asset: dict[str, Any], scene: str | None) -> bool:
    if scene is None:
        return True
    return scene == asset.get("sceneName") or scene == asset.get("testScenario")


class CaseQueryService:
    def __init__(self, store: CaseAssetStore) -> None:
        self.store = store

    def query(
        self,
        feature_key: str | None,
        feature: str | None,
        scene: str | None,
        module_name: str | None = None,
    ) -> dict[str, Any]:
        resolved_feature = _trim_to_null(feature)
        resolved_scene = _trim_to_null(scene)
        resolved_key = _trim_to_null(feature_key)
        resolved_module = _trim_to_null(module_name)
        if resolved_key is not None:
            parsed = parse(resolved_key)
            resolved_key = parsed.to_key()
            if resolved_feature is None:
                resolved_feature = parsed.feature
            if resolved_scene is None:
                resolved_scene = parsed.scene
        if resolved_feature is None and resolved_key is None:
            raise ValueError("请提供 feature 或 featureKey")
        matched = []
        for asset in self.store.list_all():
            if not is_published_official(asset):
                continue
            if not _matches_feature(asset, resolved_feature, resolved_key):
                continue
            if not _matches_scene(asset, resolved_scene):
                continue
            if resolved_module is not None and (asset.get("moduleName") or "") != resolved_module:
                continue
            matched.append(asset)
        return {
            "feature": resolved_feature,
            "scene": resolved_scene,
            "featureKey": resolved_key,
            "moduleName": resolved_module,
            "source": self.store.get_source(),
            "total": len(matched),
            "items": matched,
        }

    def query_for_map(self, feature_key: str) -> dict[str, Any]:
        exact = self.query(feature_key, None, None)
        if exact["items"]:
            return exact
        parts = parse(feature_key)
        system_prefix = parts.domain + SEPARATOR + parts.system + SEPARATOR
        matched = []
        for asset in self.store.list_all():
            if not is_published_official(asset):
                continue
            if parts.feature != asset.get("featureName"):
                continue
            asset_key = asset.get("featureKey")
            if asset_key and str(asset_key).startswith(system_prefix):
                matched.append(asset)
        return {
            "feature": parts.feature,
            "scene": parts.scene,
            "featureKey": parts.to_key(),
            "moduleName": None,
            "source": self.store.get_source(),
            "total": len(matched),
            "items": matched,
        }
