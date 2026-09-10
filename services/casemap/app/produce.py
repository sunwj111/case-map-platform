from __future__ import annotations

import csv
import hashlib
import io
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from threading import Lock
from typing import Any
from uuid import uuid4

from app.assets import CaseAssetStore
from app.feature_key import join
from app.hierarchy import HierarchyStore
from app.platform_fields import resolve_project_context
from app.xmind import parse_xmind_archive

CASE_ID_KEYS = ["caseid", "用例id", "用例编号", "编号", "id"]
CASE_NAME_KEYS = ["casename", "用例名称", "用例名", "name", "title", "标题", "用例"]
STEP_KEYS = ["step", "steps", "步骤", "操作步骤", "测试步骤"]
EXPECTED_KEYS = ["expectedresults", "expected", "预期结果", "期望结果", "预期", "期望"]
SCENE_KEYS = ["scene", "场景", "业务场景", "流程节点", "节点"]
FEATURE_KEYS = ["functionpoint", "feature", "功能点", "featurename", "功能", "模块功能"]
MODULE_KEYS = ["module", "belongplatform", "模块", "平台", "所属模块", "belongmodule"]
STOP_WORDS = {
    "以及", "或者", "并且", "然后", "进行", "可以", "需要", "如果", "当", "的", "了", "是", "在",
    "我们", "系统", "用户", "测试", "用例", "知识库", "流程", "业务", "说明", "如下", "包括",
    "主要", "相关", "一般", "时候", "之后", "之前", "通过", "完成", "支持",
}


def _now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def extension(file_name: str | None) -> str:
    name = "" if file_name is None else file_name.strip().lower()
    index = name.rfind(".")
    return "" if index < 0 else name[index + 1:]


def decode_text(content: bytes) -> tuple[str, dict[str, Any] | None]:
    try:
        text = content.decode("utf-8")
        risk = None
    except UnicodeDecodeError:
        text = content.decode("gb18030", errors="replace")
        risk = {"code": "ENCODING_FALLBACK", "message": "文件不是有效 UTF-8，已按 GB18030 解析", "row": None}
    if text.startswith("\ufeff"):
        text = text[1:]
    return text, risk


def _compact(value: Any) -> str:
    return re.sub(r"\s+", " ", "" if value is None else str(value)).strip()


def _normalize_header(value: str) -> str:
    return re.sub(r"[\s_\-]+", "", value).lower()


def _find_header_index(headers: list[str], keys: list[str]) -> int:
    normalized_keys = [_normalize_header(key) for key in keys]
    for index, header in enumerate(headers):
        normalized = _normalize_header(header)
        if normalized in normalized_keys:
            return index
    for index, header in enumerate(headers):
        normalized = _normalize_header(header)
        if len(normalized) < 2:
            continue
        if any(len(key) >= 2 and (key in normalized or normalized in key) for key in normalized_keys):
            return index
    return -1


class JsonStore:
    def __init__(self, data_file: Path, collection_key: str) -> None:
        self.data_file = data_file
        self.collection_key = collection_key
        self._lock = Lock()
        self.items: dict[str, dict[str, Any]] = {}
        if data_file.exists():
            payload = json.loads(data_file.read_text(encoding="utf-8"))
            for item in payload.get(self.collection_key) or []:
                self.items[item["id"]] = item

    def save(self) -> None:
        self.data_file.parent.mkdir(parents=True, exist_ok=True)
        payload = {"updatedAt": _now(), self.collection_key: list(self.items.values())}
        temporary = self.data_file.with_suffix(self.data_file.suffix + ".tmp")
        temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        temporary.replace(self.data_file)


def refresh_gate(batch: dict[str, Any]) -> None:
    batch["casesRequired"] = True
    batch["knowledgeRequired"] = batch.get("mode") == "standard"
    batch["readyForKeywordCalibration"] = bool(batch.get("casesImported")) and (
        not batch["knowledgeRequired"] or bool(batch.get("knowledgeImported"))
    )
    batch["readyForMapDraft"] = batch["readyForKeywordCalibration"] and bool(batch.get("keywordsConfirmed"))
    missing = []
    if not batch.get("casesImported"):
        missing.append("cases")
    if batch["knowledgeRequired"] and not batch.get("knowledgeImported"):
        missing.append("knowledge")
    batch["missingInputs"] = missing
    published_count = int(batch.get("publishedCount") or 0)
    if published_count > 0:
        batch["status"] = "ASSETS_PUBLISHED"
    elif batch.get("reviewQueueGenerated"):
        batch["status"] = "REVIEW_IN_PROGRESS"
    elif batch.get("mapDraftGenerated"):
        batch["status"] = "MAP_DRAFT_GENERATED"
    elif batch.get("readyForMapDraft"):
        batch["status"] = "READY_FOR_MAP_DRAFT"
    elif batch.get("readyForKeywordCalibration"):
        batch["status"] = "READY_FOR_KEYWORD_CALIBRATION"
    elif not batch.get("casesImported") and batch["knowledgeRequired"] and not batch.get("knowledgeImported"):
        batch["status"] = "WAITING_FOR_CASES_AND_KNOWLEDGE"
    elif not batch.get("casesImported"):
        batch["status"] = "WAITING_FOR_CASES"
    else:
        batch["status"] = "WAITING_FOR_KNOWLEDGE"


def parse_cases(file_name: str, content: bytes, max_cases: int = 10000) -> dict[str, Any]:
    if not (file_name or "").strip():
        raise ValueError("文件名不能为空")
    if not content:
        raise ValueError("用例文件为空")
    suffix = extension(file_name)
    if suffix in {"csv", "txt"}:
        text, risk = decode_text(content)
        matrix = list(csv.reader(io.StringIO(text)))
        risks = [risk] if risk else []
        return _parse_matrix(file_name, content, "csv", matrix, None, None, risks, max_cases)
    if suffix in {"xlsx", "xls"}:
        from openpyxl import load_workbook
        workbook = load_workbook(io.BytesIO(content), data_only=True)
        if not workbook.sheetnames:
            raise ValueError("Excel 文件没有工作表")
        sheet = workbook[workbook.sheetnames[0]]
        matrix = []
        for row in sheet.iter_rows(values_only=True):
            matrix.append(["" if cell is None else str(cell) for cell in row])
        return _parse_matrix(file_name, content, suffix, matrix, sheet.title, len(workbook.sheetnames), [], max_cases)
    if suffix == "xmind":
        parsed = parse_xmind_archive(content)
        rows = parsed["rows"]
        if not rows:
            raise ValueError("用例文件中没有可导入的用例")
        if len(rows) > max_cases:
            raise ValueError("单批次用例数不能超过 " + str(max_cases))
        return {
            "metadata": {
                "fileName": file_name, "format": parsed["format"], "size": len(content),
                "sha256": sha256_bytes(content), "rowCount": len(rows),
                "sheetName": None, "sheetCount": None,
            },
            "rows": rows,
            "risks": [{"code": "GENERATED_XMIND_CASE_IDS", "message": "已按 XMind 主题生成批次内临时用例 ID", "row": None}],
        }
    raise ValueError("暂不支持该用例文件格式：" + file_name)


def _parse_matrix(
    file_name: str, content: bytes, fmt: str, matrix: list[list[str]],
    sheet_name: str | None, sheet_count: int | None, risks: list[dict[str, Any]], max_cases: int,
) -> dict[str, Any]:
    first_content = next((index for index, row in enumerate(matrix) if any(_compact(cell) for cell in row)), -1)
    if first_content < 0:
        raise ValueError("用例文件为空")
    first_row = matrix[first_content]
    name_index = _find_header_index(first_row, CASE_NAME_KEYS)
    has_header = name_index >= 0
    if not has_header:
        mapping = {"id": -1, "name": 0, "step": 1, "expected": 2, "scene": 3, "feature": -1, "module": -1}
        risks.append({"code": "HEADER_NOT_DETECTED", "message": "未识别到表头，已按用例名称、步骤、预期、场景的默认列顺序解析", "row": first_content + 1})
        start_index = first_content
    else:
        mapping = {
            "id": _find_header_index(first_row, CASE_ID_KEYS),
            "name": name_index,
            "step": _find_header_index(first_row, STEP_KEYS),
            "expected": _find_header_index(first_row, EXPECTED_KEYS),
            "scene": _find_header_index(first_row, SCENE_KEYS),
            "feature": _find_header_index(first_row, FEATURE_KEYS),
            "module": _find_header_index(first_row, MODULE_KEYS),
        }
        _append_missing_column_risks(mapping, risks, first_content + 1)
        start_index = first_content + 1
    rows = []
    original_ids: set[str] = set()
    for matrix_index in range(start_index, len(matrix)):
        if len(rows) >= max_cases:
            raise ValueError("单批次用例数不能超过 " + str(max_cases))
        cells = matrix[matrix_index]
        if not any(_compact(cell) for cell in cells):
            continue
        source_row = matrix_index + 1
        case_name = _cell(cells, mapping["name"])
        if not case_name:
            risks.append({"code": "EMPTY_CASE_NAME", "message": "已跳过用例名称为空的行", "row": source_row})
            continue
        original_id = _cell(cells, mapping["id"])
        if not original_id:
            original_id = "IMPORT-ROW-" + str(source_row)
            risks.append({"code": "MISSING_ORIGINAL_CASE_ID", "message": "缺少原始用例 ID，已生成批次内临时 ID", "row": source_row})
        if original_id in original_ids:
            risks.append({"code": "DUPLICATE_ORIGINAL_CASE_ID", "message": "原始用例 ID 重复：" + original_id, "row": source_row})
        original_ids.add(original_id)
        rows.append({
            "originalCaseId": original_id,
            "caseName": case_name,
            "step": _cell(cells, mapping["step"]),
            "expected": _cell(cells, mapping["expected"]),
            "scene": _cell(cells, mapping["scene"]),
            "feature": _cell(cells, mapping["feature"]),
            "module": _cell(cells, mapping["module"]),
            "sourceRowNumber": source_row,
        })
    if not rows:
        raise ValueError("用例文件中没有可导入的用例")
    return {
        "metadata": {
            "fileName": file_name, "format": fmt, "size": len(content),
            "sha256": sha256_bytes(content), "rowCount": len(rows),
            "sheetName": sheet_name, "sheetCount": sheet_count,
        },
        "rows": rows,
        "risks": risks,
    }


def _cell(cells: list[str], index: int) -> str:
    if index < 0 or index >= len(cells):
        return ""
    return _compact(cells[index])


def _append_missing_column_risks(mapping: dict[str, int], risks: list[dict[str, Any]], header_row: int) -> None:
    if mapping["id"] < 0:
        risks.append({"code": "MISSING_CASE_ID_COLUMN", "message": "未识别原始用例 ID 列", "row": header_row})
    if mapping["step"] < 0:
        risks.append({"code": "MISSING_STEP_COLUMN", "message": "未识别测试步骤列", "row": header_row})
    if mapping["expected"] < 0:
        risks.append({"code": "MISSING_EXPECTED_COLUMN", "message": "未识别预期结果列", "row": header_row})
    if mapping["scene"] < 0:
        risks.append({"code": "MISSING_SCENE_COLUMN", "message": "未识别场景列", "row": header_row})
    if mapping["feature"] < 0:
        risks.append({"code": "MISSING_FEATURE_COLUMN", "message": "未识别功能点列", "row": header_row})


def parse_knowledge(file_name: str, content: bytes) -> dict[str, Any]:
    if not (file_name or "").strip():
        raise ValueError("文件名不能为空")
    if not content:
        raise ValueError("知识库文件为空")
    suffix = extension(file_name)
    if suffix in {"txt", "md", "markdown"}:
        text, risk = decode_text(content)
        if not text.strip():
            raise ValueError("知识库文件为空")
        return {
            "metadata": {
                "fileName": file_name, "format": suffix or "txt", "size": len(content),
                "sha256": sha256_bytes(content), "rowCount": None, "sheetName": None, "sheetCount": None,
            },
            "text": text,
            "lineCount": len(text.splitlines()),
            "risks": [risk] if risk else [],
        }
    if suffix in {"xlsx", "xls"}:
        return _parse_knowledge_workbook(file_name, content, suffix)
    if suffix == "xmind":
        parsed = parse_xmind_archive(content)
        text = parsed["knowledgeText"]
        if not text.strip():
            raise ValueError("知识库文件为空")
        return {
            "metadata": {
                "fileName": file_name, "format": parsed["format"], "size": len(content),
                "sha256": sha256_bytes(content), "rowCount": parsed["nodeCount"],
                "sheetName": None, "sheetCount": None,
            },
            "text": text,
            "lineCount": len(text.splitlines()),
            "risks": [],
        }
    raise ValueError("暂不支持该知识库文件格式：" + file_name)


def _parse_knowledge_workbook(file_name: str, content: bytes, suffix: str) -> dict[str, Any]:
    from openpyxl import load_workbook
    workbook = load_workbook(io.BytesIO(content), data_only=True)
    if not workbook.sheetnames:
        raise ValueError("知识库 Excel 没有工作表")
    sheet = workbook[workbook.sheetnames[0]]
    knowledge_lines = []
    for row in sheet.iter_rows(values_only=True):
        cell_values = [_compact(cell) for cell in row if _compact(cell)]
        if cell_values:
            knowledge_lines.append("- " + "、".join(cell_values))
    if not knowledge_lines:
        raise ValueError("知识库 Excel 中没有可读取内容")
    text = "# 从 Excel 导入的知识草稿\n\n" + "\n".join(knowledge_lines)
    return {
        "metadata": {
            "fileName": file_name, "format": suffix, "size": len(content),
            "sha256": sha256_bytes(content), "rowCount": len(knowledge_lines),
            "sheetName": sheet.title, "sheetCount": len(workbook.sheetnames),
        },
        "text": text,
        "lineCount": len(text.splitlines()),
        "risks": [],
    }


def extract_knowledge(text: str, source_version: str) -> dict[str, Any]:
    source_text = (text or "").strip()
    if not source_text:
        raise ValueError("知识文本不能为空")
    scenes: dict[str, dict[str, Any]] = {}
    features: dict[str, dict[str, Any]] = {}
    rules: dict[str, dict[str, Any]] = {}
    nodes: dict[str, dict[str, Any]] = {}
    has_sections = False
    for line in source_text.splitlines():
        stripped = line.strip()
        heading = re.match(r"^(#{1,3})\s*(.+?)\s*$", stripped)
        if heading:
            has_sections = True
            title = heading.group(2).strip()
            if "场景" in title:
                _add_candidate(scenes, title.replace("场景", "").strip() or title, "章节标题·场景", 80)
            elif "功能" in title or "能力" in title:
                _add_candidate(features, title, "章节标题·功能点", 80)
            elif "规则" in title or "校验" in title:
                _add_candidate(rules, title, "章节标题·规则", 75)
            elif "节点" in title:
                _add_candidate(nodes, title, "章节标题·节点", 75)
            continue
        lead_scene = re.match(r"^(?:业务)?场景[:：]\s*(.+)$", stripped)
        if lead_scene:
            _add_candidate(scenes, lead_scene.group(1), "行首·场景", 85)
            continue
        lead_feature = re.match(r"^(?:功能点|功能|能力|模块)[:：]\s*(.+)$", stripped)
        if lead_feature:
            _add_candidate(features, lead_feature.group(1), "行首·功能点", 85)
            continue
        lead_rule = re.match(r"^(?:规则|约束|校验)[:：]\s*(.+)$", stripped)
        if lead_rule:
            _add_candidate(rules, lead_rule.group(1), "行首·规则", 80)
            continue
        lead_node = re.match(r"^(?:流程节点|节点)[:：]\s*(.+)$", stripped)
        if lead_node:
            _add_candidate(nodes, lead_node.group(1), "行首·节点", 80)
    for match in re.finditer(r"(?:支持|提供|完成|实现|负责|用于)\s*([\u4e00-\u9fa5A-Za-z0-9]{2,16})", source_text):
        _add_candidate(features, match.group(1), "短文本·功能点", 65)
    for match in re.finditer(r"([\u4e00-\u9fa5A-Za-z0-9]{2,20}(?:必须|不可|不能|应当|禁止)[^\n。；;]{0,20})", source_text):
        _add_candidate(rules, match.group(1), "短文本·规则", 70)
    if not scenes and not features and not rules and not nodes:
        cleaned = _clean_item(source_text)
        if cleaned:
            _add_candidate(features, cleaned, "短文本·功能点", 65)
    if not scenes and not features and not rules and not nodes:
        raise ValueError("未从知识文本中抽取到有效候选，请上传知识文件或补充业务描述")
    return {
        "scenes": list(scenes.values())[:24],
        "features": list(features.values())[:30],
        "rules": list(rules.values())[:24],
        "nodes": list(nodes.values())[:24],
        "mode": "structured+nl" if has_sections else "nl",
        "sourceVersion": source_version,
    }


def _clean_item(text: str) -> str:
    cleaned = re.sub(r"\s+", "", text)
    for word in STOP_WORDS:
        cleaned = cleaned.replace(word, "")
    return cleaned[:40]


def _add_candidate(bag: dict[str, dict[str, Any]], text: str, source: str, confidence: int) -> None:
    cleaned = _compact(text)
    if len(cleaned) < 2:
        return
    current = bag.get(cleaned)
    if current is None or confidence > current["confidence"]:
        bag[cleaned] = {"text": cleaned, "source": source, "confidence": confidence}


def generate_map_draft(batch: dict[str, Any]) -> dict[str, Any]:
    case_import = batch.get("caseImport") or {}
    rows = case_import.get("rows") or []
    keywords = batch.get("keywordExtraction")
    if not rows:
        raise ValueError("请先导入历史用例")
    if keywords is None:
        raise ValueError("请先抽取并确认知识库关键字")
    if not batch.get("keywordsConfirmed"):
        raise ValueError("请先确认关键字")
    cleaned_cases = [_clean_case(row, keywords) for row in rows]
    gaps = _build_gaps(cleaned_cases, keywords)
    stats = _draft_stats(cleaned_cases, gaps)
    return {
        "batchId": batch["id"],
        "cases": cleaned_cases,
        "gaps": gaps,
        "stats": stats,
        "generatedAt": _now(),
    }


def _clean_case(row: dict[str, Any], keywords: dict[str, Any]) -> dict[str, Any]:
    searchable = _normalize(" ".join([
        row.get("caseName") or "", row.get("step") or "", row.get("expected") or "",
        row.get("scene") or "", row.get("feature") or "", row.get("module") or "",
    ]))
    matched_scene = _best_match(searchable, keywords.get("scenes") or [])
    matched_feature = _best_match(searchable, keywords.get("features") or [])
    matched_node = _best_match(searchable, keywords.get("nodes") or [])
    scene = row.get("scene") or (matched_scene["text"] if matched_scene else "未识别场景")
    feature = row.get("feature") or (matched_feature["text"] if matched_feature else "未识别功能点")
    evidence = []
    confidence = 40
    if row.get("scene"):
        confidence += 15
        evidence.append("历史用例已有场景")
    if row.get("feature"):
        confidence += 20
        evidence.append("历史用例已有功能点")
    original_id = row.get("originalCaseId") or ""
    if not original_id.startswith("IMPORT-ROW-") and not original_id.startswith("XMIND-ROW-"):
        confidence += 5
        evidence.append("保留原始用例ID")
    if matched_scene:
        confidence += 5
        evidence.append("命中知识场景：" + matched_scene["text"])
    if matched_feature:
        confidence += 10
        evidence.append("命中知识功能点：" + matched_feature["text"])
    if matched_node:
        confidence += 5
        evidence.append("命中流程节点：" + matched_node["text"])
    confidence = min(confidence, 95)
    return {
        "originalCaseId": original_id,
        "caseName": row.get("caseName"),
        "scene": scene,
        "feature": feature,
        "flowNode": matched_node["text"] if matched_node else "",
        "confidence": confidence,
        "mountAdvice": "自动挂载" if confidence >= 85 else ("待抽检" if confidence >= 70 else "人工确认"),
        "matchEvidence": evidence,
        "sourceRowNumber": row.get("sourceRowNumber"),
    }


def _best_match(searchable: str, candidates: list[dict[str, Any]]) -> dict[str, Any] | None:
    best = None
    for candidate in candidates:
        normalized = _normalize(candidate.get("text") or "")
        if len(normalized) < 2 or normalized not in searchable:
            continue
        if best is None or len(candidate.get("text") or "") > len(best.get("text") or ""):
            best = candidate
    return best


def _build_gaps(cleaned_cases: list[dict[str, Any]], keywords: dict[str, Any]) -> list[dict[str, Any]]:
    covered = {_normalize(item.get("feature") or "") for item in cleaned_cases}
    default_scene = (keywords.get("scenes") or [{"text": "待确认场景"}])[0]["text"]
    gaps = []
    for feature in keywords.get("features") or []:
        source = feature.get("source") or ""
        text = feature.get("text") or ""
        if text.startswith("/"):
            continue
        if not any(token in source for token in ("章节标题·功能点", "章节·功能点", "行首·功能点")) and source != "人工确认":
            continue
        normalized = _normalize(text)
        covered_hit = any(
            existing == normalized or existing in normalized or normalized in existing
            for existing in covered
        )
        if not covered_hit:
            gaps.append({
                "id": "GAP-" + str(len(gaps) + 1).zfill(3),
                "scene": default_scene,
                "feature": text,
                "reason": "知识库功能点未匹配历史用例",
            })
        if len(gaps) >= 50:
            break
    return gaps


def _draft_stats(cleaned_cases: list[dict[str, Any]], gaps: list[dict[str, Any]]) -> dict[str, int]:
    auto_mounted = sum(1 for item in cleaned_cases if item["confidence"] >= 85)
    review_required = sum(1 for item in cleaned_cases if 70 <= item["confidence"] < 85)
    manual_required = sum(1 for item in cleaned_cases if item["confidence"] < 70)
    return {
        "totalCases": len(cleaned_cases),
        "autoMounted": auto_mounted,
        "reviewRequired": review_required,
        "manualRequired": manual_required,
        "gapCount": len(gaps),
    }


def _normalize(value: str) -> str:
    return re.sub(r"\s+", "", (value or "").lower())


class ProduceService:
    def __init__(
        self,
        hierarchy_store: HierarchyStore,
        case_assets: CaseAssetStore,
        batch_file: Path,
        review_file: Path,
    ) -> None:
        self.hierarchy_store = hierarchy_store
        self.case_assets = case_assets
        self.batches = JsonStore(batch_file, "batches")
        self.reviews = JsonStore(review_file, "items")

    def create(self, body: dict[str, Any]) -> dict[str, Any]:
        mode = body.get("mode") or "standard"
        if mode != "standard":
            raise ValueError("当前仅开放标准模式")
        context = resolve_project_context(body)
        current_time = _now()
        batch = {
            "id": str(uuid4()),
            "domain": context["domain"],
            "system": context["system"],
            "moduleName": context["moduleName"],
            "processNode": context["processNode"],
            "ziroom_domain": context["domain"],
            "system_ref": context["system"],
            "module_name": context["moduleName"],
            "process_node": context["processNode"],
            "mode": "standard",
            "casesImported": False,
            "knowledgeImported": False,
            "keywordsConfirmed": False,
            "mapDraftGenerated": False,
            "reviewQueueGenerated": False,
            "publishedCount": 0,
            "caseImport": None,
            "knowledgeImport": None,
            "keywordExtraction": None,
            "mapDraft": None,
            "createdAt": current_time,
            "updatedAt": current_time,
        }
        refresh_gate(batch)
        self.batches.items[batch["id"]] = batch
        self.batches.save()
        return batch

    def find(self, batch_id: str) -> dict[str, Any] | None:
        return self.batches.items.get(batch_id)

    def get(self, batch_id: str) -> dict[str, Any]:
        batch = self.find(batch_id)
        if batch is None:
            raise ValueError("导入批次不存在：" + batch_id)
        return batch

    def import_cases(self, batch_id: str, file_name: str, content: bytes) -> dict[str, Any]:
        batch = self._editable(batch_id)
        parsed = parse_cases(file_name, content)
        batch["caseImport"] = parsed
        batch["casesImported"] = True
        batch["keywordsConfirmed"] = False
        batch["mapDraftGenerated"] = False
        batch["mapDraft"] = None
        batch["updatedAt"] = _now()
        refresh_gate(batch)
        self.batches.save()
        return parsed

    def import_knowledge(self, batch_id: str, file_name: str, content: bytes) -> dict[str, Any]:
        batch = self._editable(batch_id)
        parsed = parse_knowledge(file_name, content)
        batch["knowledgeImport"] = parsed
        batch["knowledgeImported"] = True
        batch["keywordExtraction"] = None
        batch["keywordsConfirmed"] = False
        batch["mapDraftGenerated"] = False
        batch["mapDraft"] = None
        batch["updatedAt"] = _now()
        refresh_gate(batch)
        self.batches.save()
        return parsed

    def extract_knowledge(self, batch_id: str) -> dict[str, Any]:
        batch = self._editable(batch_id)
        knowledge = batch.get("knowledgeImport") or {}
        text = knowledge.get("text") or ""
        if not text.strip():
            raise ValueError("请先上传或粘贴知识库内容")
        source_version = (knowledge.get("metadata") or {}).get("sha256") or sha256_bytes(text.encode("utf-8"))
        extraction = extract_knowledge(text, source_version)
        batch["keywordExtraction"] = extraction
        batch["keywordsConfirmed"] = False
        batch["mapDraftGenerated"] = False
        batch["mapDraft"] = None
        batch["updatedAt"] = _now()
        refresh_gate(batch)
        self.batches.save()
        return extraction

    def import_knowledge_text(self, batch_id: str, source_name: str | None, text: str | None) -> dict[str, Any]:
        batch = self._editable(batch_id)
        normalized = (text or "").strip()
        if not normalized:
            raise ValueError("知识文本不能为空")
        existing = ((batch.get("knowledgeImport") or {}).get("text") or "").strip()
        combined = normalized if not existing else existing + "\n\n# 补充自然语言知识\n\n" + normalized
        content = combined.encode("utf-8")
        name = (source_name or "").strip() or "pasted-knowledge.md"
        if existing:
            previous_name = ((batch.get("knowledgeImport") or {}).get("metadata") or {}).get("fileName") or "knowledge"
            name = previous_name + " + " + name
        metadata = {
            "fileName": name,
            "format": "pasted-text" if not existing else "combined-knowledge",
            "size": len(content),
            "sha256": sha256_bytes(content),
            "rowCount": None, "sheetName": None, "sheetCount": None,
        }
        extraction = extract_knowledge(combined, metadata["sha256"])
        batch["knowledgeImport"] = {"metadata": metadata, "text": combined, "lineCount": combined.count("\n") + 1, "risks": []}
        batch["knowledgeImported"] = True
        batch["keywordExtraction"] = extraction
        batch["keywordsConfirmed"] = False
        batch["mapDraftGenerated"] = False
        batch["mapDraft"] = None
        batch["updatedAt"] = _now()
        refresh_gate(batch)
        self.batches.save()
        return extraction

    def confirm_keywords(self, batch_id: str, body: dict[str, Any] | None) -> dict[str, Any]:
        batch = self._editable(batch_id)
        refresh_gate(batch)
        if not batch.get("readyForKeywordCalibration"):
            raise ValueError("标准模式需先完成历史用例和知识库导入")
        if batch.get("keywordExtraction") is None:
            raise ValueError("请先抽取知识库关键字")
        if body and any(body.get(key) for key in ("scenes", "features", "rules", "nodes")):
            current = batch["keywordExtraction"]
            batch["keywordExtraction"] = {
                **current,
                "scenes": _confirmed(body.get("scenes") or [], current.get("scenes") or []),
                "features": _confirmed(body.get("features") or [], current.get("features") or []),
                "rules": _confirmed(body.get("rules") or [], current.get("rules") or []),
                "nodes": _confirmed(body.get("nodes") or [], current.get("nodes") or []),
                "mode": "confirmed",
            }
        batch["keywordsConfirmed"] = True
        batch["updatedAt"] = _now()
        refresh_gate(batch)
        self.batches.save()
        return batch

    def generate_draft(self, batch_id: str) -> dict[str, Any]:
        batch = self._editable(batch_id)
        draft = generate_map_draft(batch)
        self._create_review_queue(batch, draft)
        batch["mapDraft"] = draft
        batch["mapDraftGenerated"] = True
        batch["reviewQueueGenerated"] = True
        batch["updatedAt"] = _now()
        refresh_gate(batch)
        self.batches.save()
        self.reviews.save()
        return draft

    def query_reviews(
        self, batch_id: str | None, system: str | None, status: str | None,
        kind: str | None, pool: str | None, keyword: str | None,
    ) -> dict[str, Any]:
        items = list(self.reviews.items.values())
        if batch_id:
            items = [item for item in items if item.get("batchId") == batch_id]
        if status:
            items = [item for item in items if item.get("status") == status]
        if kind:
            items = [item for item in items if item.get("kind") == kind]
        if keyword:
            needle = _normalize(keyword)
            items = [item for item in items if needle in _normalize(" ".join([
                str(item.get("caseName") or ""), str(item.get("scene") or ""),
                str(item.get("feature") or ""), str(item.get("originalCaseId") or ""),
            ]))]
        if pool == "A":
            items = [item for item in items if int(item.get("confidence") or 0) >= 85]
        elif pool == "B":
            items = [item for item in items if 70 <= int(item.get("confidence") or 0) < 85]
        elif pool == "C":
            items = [item for item in items if int(item.get("confidence") or 0) < 70]
        counts = {
            "total": len(items),
            "pending": sum(1 for item in items if item.get("status") == "待评审"),
            "confirmed": sum(1 for item in items if item.get("status") == "已确认"),
            "discarded": sum(1 for item in items if item.get("status") == "已废弃"),
            "published": sum(1 for item in items if item.get("status") == "已发布"),
        }
        return {"total": len(items), "counts": counts, "items": items}

    def update_review(self, review_id: str, body: dict[str, Any]) -> dict[str, Any]:
        item = self._review(review_id)
        for field_name, key in (
            ("caseName", "caseName"), ("scene", "scene"), ("feature", "feature"),
            ("step", "step"), ("expected", "expected"), ("priority", "priority"), ("flowNode", "flowNode"),
        ):
            if body.get(field_name) is not None:
                item[key] = str(body[field_name]).strip()
        item["updatedAt"] = _now()
        self.reviews.save()
        return item

    def confirm_review(self, review_id: str, body: dict[str, Any] | None) -> dict[str, Any]:
        item = self._pending(review_id)
        self._validate_confirm(item)
        item["status"] = "已确认"
        _add_op(item, "确认", _operator(body), (body or {}).get("comment") or "")
        item["updatedAt"] = _now()
        self.reviews.save()
        return item

    def discard_review(self, review_id: str, body: dict[str, Any] | None) -> dict[str, Any]:
        item = self._pending(review_id)
        item["status"] = "已废弃"
        _add_op(item, "废弃", _operator(body), (body or {}).get("comment") or "")
        item["updatedAt"] = _now()
        self.reviews.save()
        return item

    def confirm_batch(self, body: dict[str, Any]) -> dict[str, Any]:
        review_ids = body.get("reviewIds") or []
        succeeded = []
        failures = []
        for review_id in review_ids:
            try:
                self.confirm_review(review_id, body)
                succeeded.append(review_id)
            except ValueError as error:
                failures.append({"reviewId": review_id, "detail": str(error)})
        return {
            "requested": len(review_ids),
            "succeeded": len(succeeded),
            "failed": len(failures),
            "succeededIds": succeeded,
            "failures": failures,
        }

    def publish_batch(self, batch_id: str, body: dict[str, Any] | None) -> dict[str, Any]:
        batch = self.get(batch_id)
        confirmed = [
            item for item in self.reviews.items.values()
            if item.get("batchId") == batch_id and item.get("status") == "已确认"
        ]
        if not confirmed:
            raise ValueError("当前批次没有已确认且待发布的用例")
        published_at = _now()
        assets = [_to_official_asset(batch, item, published_at) for item in confirmed]
        self.case_assets.publish(assets)
        operator = _operator(body)
        for item in confirmed:
            item["status"] = "已发布"
            item["officialCaseId"] = _official_case_id(item["id"])
            _add_op(item, "发布", operator, (body or {}).get("comment") or "")
            item["updatedAt"] = published_at
        batch["publishedCount"] = int(batch.get("publishedCount") or 0) + len(assets)
        batch["updatedAt"] = published_at
        refresh_gate(batch)
        self.reviews.save()
        self.batches.save()
        return {
            "batchId": batch_id,
            "publishedCount": len(assets),
            "caseIds": [asset["id"] for asset in assets],
            "publishedAt": published_at,
        }

    def _editable(self, batch_id: str) -> dict[str, Any]:
        batch = self.get(batch_id)
        if batch.get("reviewQueueGenerated"):
            raise ValueError("当前批次已进入评审，不能重新导入或生成草稿")
        return batch

    def _review(self, review_id: str) -> dict[str, Any]:
        item = self.reviews.items.get(review_id)
        if item is None:
            raise ValueError("评审项不存在：" + review_id)
        return item

    def _pending(self, review_id: str) -> dict[str, Any]:
        item = self._review(review_id)
        if item.get("status") != "待评审":
            raise ValueError("仅待评审状态允许此操作，当前状态：" + str(item.get("status")))
        return item

    def _validate_confirm(self, item: dict[str, Any]) -> None:
        for field_name, label in (("caseName", "用例名称"), ("scene", "业务场景"), ("feature", "功能点")):
            if not (item.get(field_name) or "").strip():
                raise ValueError(label + "不能为空")
        for field_name, label in (("scene", "业务场景"), ("feature", "功能点")):
            value = item.get(field_name) or ""
            if "/" in value or "\n" in value or "\r" in value:
                raise ValueError(label + "不能包含斜杠或换行")
        if item.get("priority") not in {"P0", "P1", "P2"}:
            raise ValueError("优先级仅支持 P0、P1、P2")
        if item.get("kind") == "知识缺口":
            if not (item.get("step") or "").strip():
                raise ValueError("缺口用例步骤不能为空")
            if not (item.get("expected") or "").strip():
                raise ValueError("缺口用例预期不能为空")

    def _create_review_queue(self, batch: dict[str, Any], draft: dict[str, Any]) -> None:
        source_rows = {(row.get("originalCaseId")): row for row in (batch.get("caseImport") or {}).get("rows") or []}
        keep_ids = []
        historical_index = 1
        current_time = _now()
        for cleaned in draft.get("cases") or []:
            review_id = _review_id(batch["id"], "H", historical_index)
            historical_index += 1
            source_row = source_rows.get(cleaned.get("originalCaseId")) or {}
            item = _base_review(review_id, batch["id"], "历史用例", current_time)
            item.update({
                "originalCaseId": cleaned.get("originalCaseId"),
                "caseName": cleaned.get("caseName"),
                "scene": cleaned.get("scene"),
                "feature": cleaned.get("feature"),
                "flowNode": cleaned.get("flowNode"),
                "step": source_row.get("step") or "",
                "expected": source_row.get("expected") or "",
                "priority": "P0" if int(cleaned.get("confidence") or 0) >= 85 else "P1",
                "confidence": cleaned.get("confidence"),
                "matchEvidence": cleaned.get("matchEvidence") or [],
            })
            self.reviews.items[review_id] = item
            keep_ids.append(review_id)
        gap_index = 1
        for gap in draft.get("gaps") or []:
            review_id = _review_id(batch["id"], "G", gap_index)
            gap_index += 1
            item = _base_review(review_id, batch["id"], "知识缺口", current_time)
            item.update({
                "originalCaseId": gap.get("id"),
                "caseName": "缺口用例：" + str(gap.get("feature") or ""),
                "scene": gap.get("scene"),
                "feature": gap.get("feature"),
                "flowNode": "",
                "step": "",
                "expected": "",
                "priority": "P1",
                "confidence": 60,
                "matchEvidence": [gap.get("reason")],
            })
            self.reviews.items[review_id] = item
            keep_ids.append(review_id)
        for existing_id in list(self.reviews.items):
            existing = self.reviews.items[existing_id]
            if existing.get("batchId") == batch["id"] and existing_id not in keep_ids and existing.get("status") == "待评审":
                del self.reviews.items[existing_id]


def _confirmed(values: list[str], extracted: list[dict[str, Any]]) -> list[dict[str, Any]]:
    unique: list[str] = []
    for value in values:
        text = (value or "").strip()
        if text and text not in unique:
            unique.append(text)
    result = []
    for value in unique:
        original = next((item.get("source") for item in extracted if item.get("text") == value), "")
        source = "人工确认" if not original else "人工确认·" + original
        result.append({"text": value, "source": source, "confidence": 100})
    return result


def _review_id(batch_id: str, kind: str, index: int) -> str:
    return "RV-" + batch_id[:8] + "-" + kind + "-" + str(index).zfill(3)


def _official_case_id(review_id: str) -> str:
    return "CA-" + review_id


def _operator(body: dict[str, Any] | None) -> str:
    operator = (body or {}).get("operator")
    return operator.strip() if operator else "当前用户"


def _add_op(item: dict[str, Any], action: str, operator: str, detail: str) -> None:
    operations = list(item.get("operations") or [])
    operations.append({"action": action, "operator": operator, "detail": (detail or "").strip(), "at": _now()})
    item["operations"] = operations


def _base_review(review_id: str, batch_id: str, kind: str, current_time: str) -> dict[str, Any]:
    return {
        "id": review_id,
        "batchId": batch_id,
        "kind": kind,
        "status": "待评审",
        "createdAt": current_time,
        "updatedAt": current_time,
        "operations": [{
            "action": "生成",
            "operator": "系统",
            "detail": "历史用例清洗进入评审" if kind == "历史用例" else "知识缺口进入评审",
            "at": current_time,
        }],
    }


def _to_official_asset(batch: dict[str, Any], item: dict[str, Any], published_at: str) -> dict[str, Any]:
    return {
        "id": _official_case_id(item["id"]),
        "name": item.get("caseName"),
        "priority": item.get("priority"),
        "testScenario": item.get("scene"),
        "featureName": item.get("feature"),
        "sceneName": item.get("scene"),
        "featureKey": join(batch["domain"], batch["system"], item.get("scene") or "", item.get("feature") or ""),
        "status": "已确认",
        "lifecycle": "已发布",
        "api": "",
        "confidence": item.get("confidence"),
        "step": item.get("step"),
        "expected": item.get("expected"),
        "sourceBatchId": item.get("batchId"),
        "sourceReviewId": item["id"],
        "originalCaseId": item.get("originalCaseId"),
        "knowledgeSourceVersion": ((batch.get("keywordExtraction") or {}).get("sourceVersion") or ""),
        "sourceType": item.get("kind"),
        "flowNode": item.get("flowNode"),
        "moduleName": batch.get("moduleName") or "",
        "processNode": batch.get("processNode") or batch.get("process_node") or "",
        "createdAt": published_at,
        "updatedAt": published_at,
    }
