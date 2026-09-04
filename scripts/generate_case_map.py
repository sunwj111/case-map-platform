#!/usr/bin/env python3
import argparse
import json
import re
from collections import Counter, defaultdict
from pathlib import Path

import pandas as pd
from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter


COMMON_TEST_TYPES = [
    {"name": "状态流转", "keywords": ["状态", "流转", "待提交", "待审核", "审核通过", "审核拒绝", "作废", "失效", "取消"]},
    {"name": "规则校验", "keywords": ["规则", "校验", "拦截", "判断", "必须", "不可", "异常", "错误"]},
    {"name": "金额计算", "keywords": ["金额", "合计", "总价", "含税", "不含税", "去利润", "单价", "数量", "价格", "税"]},
    {"name": "主流程", "keywords": ["成功", "正常", "生成", "发起", "提交", "通过"]},
    {"name": "异常流", "keywords": ["失败", "异常", "拒绝", "驳回", "不允许", "为空", "非法", "错误"]},
    {"name": "边界值", "keywords": ["0", "为空", "最大", "最小", "边界", "四舍五入", "精度", "比例"]},
    {"name": "数据一致性", "keywords": ["一致", "同步", "落库", "保存", "字段", "记录", "明细"]},
    {"name": "展示校验", "keywords": ["展示", "名称", "页面", "弹窗", "tab", "按钮"]},
]

CASE_TEXT_COLUMNS = [
    "id",
    "casename",
    "function_point",
    "pre_condition",
    "step",
    "expected_results",
    "prod_requirements_name",
    "relation_requirement",
]

OUTPUT_TEST_TYPES = ["主流程", "异常流", "规则校验", "金额计算", "状态流转", "数据一致性", "边界值", "展示校验"]


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def default_config():
    config_path = Path(__file__).resolve().parents[1] / "configs" / "quote.json"
    if config_path.exists():
        return read_json(config_path)
    return {
        "domain": "quote",
        "displayName": "家装报价领域",
        "flowNodes": [
            {"node": "报价触发与报价单生成", "description": "发起报价并生成报价单。", "risk": "高", "keywords": ["生成报价单", "发起报价", "报价接口"]},
            {"node": "金额计算与汇总", "description": "计算金额并汇总。", "risk": "高", "keywords": ["金额", "合计", "总价", "单价", "数量"]},
            {"node": "造价提交与审核", "description": "提交造价并审核。", "risk": "高", "keywords": ["提交", "审核", "通过", "拒绝"]},
        ],
        "stateTransitions": [],
        "fields": [],
        "rules": [],
    }


def load_config(domain, config_path):
    config = read_json(config_path) if config_path else default_config()
    config.setdefault("domain", domain)
    config.setdefault("displayName", domain)
    config.setdefault("flowNodes", [])
    config.setdefault("stateTransitions", [])
    config.setdefault("fields", [])
    config.setdefault("rules", [])
    merged_test_types = COMMON_TEST_TYPES + config.get("testTypes", [])
    config["testTypes"] = merged_test_types
    return config


def val(row, name):
    return "" if name not in row or pd.isna(row[name]) else str(row[name]).strip()


def compact(text):
    return re.sub(r"\s+", " ", text or "").strip()


def score_text(text, keywords):
    low = text.lower()
    return sum(1 for kw in keywords if str(kw).lower() in low)


def text_keys(text):
    return [x for x in re.findall(r"[\u4e00-\u9fa5A-Za-z0-9_]+", text or "") if len(x) > 1]


def row_text(row):
    return compact(" ".join(val(row, c) for c in CASE_TEXT_COLUMNS))


def classify_node(text, config):
    scored = []
    for item in config["flowNodes"]:
        scored.append((score_text(text, item.get("keywords", [])), item))
    scored.sort(key=lambda x: x[0], reverse=True)
    if not scored or scored[0][0] == 0:
        return "待人工确认", 35, "未命中明确流程节点关键词"
    score, item = scored[0]
    return item["node"], min(95, 55 + score * 10), f"命中节点关键词数：{score}"


def classify_type(text, config):
    scored = [(score_text(text, item.get("keywords", [])), item["name"]) for item in config["testTypes"]]
    scored.sort(key=lambda x: x[0], reverse=True)
    return scored[0][1] if scored and scored[0][0] else "功能测试"


def related_state_chain(text, config):
    best = ("", 0)
    for item in config["stateTransitions"]:
        keys = [item.get("chain", ""), item.get("source", ""), item.get("target", ""), item.get("action", "")]
        score = score_text(text, [k.split("(")[0] for k in keys if k])
        if score > best[1]:
            best = (item.get("chain", ""), score)
    return best[0] if best[1] else ""


def related_fields(text, config):
    hits = []
    for item in config["fields"]:
        keys = [item.get("field", ""), item.get("meaning", "")]
        if score_text(text, keys):
            hits.append(f"{item.get('entity', '')}.{item.get('field', '')}")
    return "、".join([h for h in hits if h])


def related_rules(text, config):
    hits = []
    for item in config["rules"]:
        keys = text_keys(item.get("name", "") + " " + item.get("description", ""))[:6]
        if score_text(text, keys):
            hits.append(item.get("id") or item.get("name", ""))
    return "、".join([h for h in hits if h])


def confidence_advice(confidence):
    if confidence >= 80:
        return "自动挂载"
    if confidence >= 60:
        return "人工抽检"
    return "人工确认"


def load_cases(path, config):
    xls = pd.ExcelFile(path)
    df = pd.read_excel(xls, sheet_name=xls.sheet_names[0])
    rows = []
    for _, row in df.iterrows():
        text = row_text(row)
        node, confidence, evidence = classify_node(text, config)
        rows.append({
            "用例ID": val(row, "id"),
            "用例名称": val(row, "casename"),
            "功能点": val(row, "function_point"),
            "等级": val(row, "test_case_level"),
            "平台": val(row, "belong_platform"),
            "原始核心标记": val(row, "core_case"),
            "需求号": val(row, "relation_requirement") or val(row, "prod_requirements_name"),
            "推荐流程节点": node,
            "推荐测试类型": classify_type(text, config),
            "关联状态链": related_state_chain(text, config),
            "关联字段": related_fields(text, config),
            "关联规则": related_rules(text, config),
            "置信度": confidence,
            "匹配依据": evidence,
            "处理建议": confidence_advice(confidence),
            "前置条件": val(row, "pre_condition"),
            "步骤": val(row, "step"),
            "预期结果": val(row, "expected_results"),
            "_全文": text,
        })
    return rows, xls.sheet_names[0], len(df.columns)


def match_count(cases, keys):
    clean_keys = [k.split("(")[0] for k in keys if k]
    return sum(1 for case in cases if score_text(case["_全文"], clean_keys))


def build_test_map(cases, config):
    counter = Counter(r["推荐流程节点"] for r in cases)
    type_by_node = defaultdict(Counter)
    for case in cases:
        type_by_node[case["推荐流程节点"]][case["推荐测试类型"]] += 1

    rows = []
    for i, node in enumerate(config["flowNodes"], 1):
        node_name = node["node"]
        hist_count = counter[node_name]
        coverage = ["有" if type_by_node[node_name][t] else "缺" for t in OUTPUT_TEST_TYPES]
        missing = "、".join(t for t, flag in zip(OUTPUT_TEST_TYPES, coverage) if flag == "缺")
        risk = node.get("risk", "中")
        priority = "P0" if hist_count == 0 and risk == "高" else ("P1" if hist_count < 3 and risk == "高" else ("P2" if hist_count == 0 else "P3"))
        rows.append(dict(zip(
            ["序号", "流程节点", "节点说明", "风险等级", "历史用例数"] + OUTPUT_TEST_TYPES + ["缺口结论", "缺口优先级"],
            [i, node_name, node.get("description", ""), risk, hist_count] + coverage + [f"补充缺失类型：{missing}" if missing else "覆盖较完整", priority],
        )))
    return rows


def build_state_matrix(cases, config):
    rows = []
    for item in config["stateTransitions"]:
        keys = [item.get("chain", ""), item.get("source", ""), item.get("target", ""), item.get("action", "")]
        count = match_count(cases, keys)
        chain = item.get("chain", "")
        source = item.get("source", "")
        target = item.get("target", "")
        action = item.get("action", "")
        rows.append({
            "状态链": chain,
            "起始状态": source,
            "目标状态": target,
            "触发动作": action,
            "流转类型": item.get("type", ""),
            "推荐节点": item.get("node", ""),
            "匹配历史用例数": count,
            "覆盖状态": "已覆盖" if count else "缺口",
            "建议用例": f"验证{chain}：{source} 经由「{action}」流转到 {target}",
        })
    return rows


def build_field_matrix(cases, config):
    rows = []
    for item in config["fields"]:
        keys = [item.get("field", ""), item.get("meaning", "")]
        count = match_count(cases, keys)
        field = item.get("field", "")
        ftype = item.get("type", "")
        rows.append({
            "实体": item.get("entity", ""),
            "字段": field,
            "业务含义": item.get("meaning", ""),
            "字段类型": ftype,
            "推荐节点": item.get("node", ""),
            "匹配历史用例数": count,
            "覆盖状态": "已覆盖" if count else "缺口",
            "建议测试点": f"校验 {field} 的{ftype}规则、边界和数据一致性",
        })
    return rows


def build_rule_matrix(cases, config):
    rows = []
    for item in config["rules"]:
        keys = text_keys(item.get("name", "") + " " + item.get("description", ""))[:8]
        count = match_count(cases, keys)
        name = item.get("name", "")
        rows.append({
            "规则ID": item.get("id", ""),
            "规则名称": name,
            "规则说明": item.get("description", ""),
            "推荐节点": item.get("node", ""),
            "测试类型": item.get("testType", "规则校验"),
            "匹配历史用例数": count,
            "覆盖状态": "已覆盖" if count else "缺口",
            "建议测试点": f"{name}：覆盖正常、异常、边界和数据一致性",
        })
    return rows


def risk_for_node(node_name, config):
    for node in config["flowNodes"]:
        if node["node"] == node_name:
            return node.get("risk", "中")
    return "中"


def build_gap_cases(state_matrix, field_matrix, rule_matrix, config):
    rows = []
    idx = 1
    for item in state_matrix:
        if item["覆盖状态"] != "缺口":
            continue
        node = item["推荐节点"] or "待人工确认"
        rows.append({
            "用例编号": f"GAP-S-{idx:03d}",
            "来源": "状态机缺口",
            "流程节点": node,
            "用例名称": item["建议用例"],
            "测试类型": "状态流转",
            "风险等级": risk_for_node(node, config),
            "前置条件": f"存在{item['状态链']}对象，当前状态为{item['起始状态']}",
            "测试步骤": f"执行「{item['触发动作']}」",
            "预期结果": f"状态变为{item['目标状态']}，操作日志、时间、原因等关联字段正确记录",
            "是否建议核心回归": "是",
        })
        idx += 1

    for item in field_matrix:
        if item["覆盖状态"] != "缺口":
            continue
        node = item["推荐节点"] or "待人工确认"
        rows.append({
            "用例编号": f"GAP-F-{idx:03d}",
            "来源": "字段缺口",
            "流程节点": node,
            "用例名称": f"验证字段「{item['字段']}」覆盖",
            "测试类型": "数据一致性" if item["字段类型"] in ["状态", "关联", "生命周期", "集合"] else "边界值",
            "风险等级": risk_for_node(node, config),
            "前置条件": f"字段含义：{item['业务含义']}；字段类型：{item['字段类型']}",
            "测试步骤": "构造正常、异常、边界数据，执行业务操作并检查展示、接口返回和落库",
            "预期结果": f"{item['实体']}.{item['字段']} 的取值、展示、落库和下游同步一致",
            "是否建议核心回归": "是" if item["字段类型"] in ["计算", "状态", "关联", "生命周期"] else "否",
        })
        idx += 1

    for item in rule_matrix:
        if item["覆盖状态"] != "缺口" and item["测试类型"] not in ["金额计算", "边界值"]:
            continue
        node = item["推荐节点"] or "待人工确认"
        rows.append({
            "用例编号": f"GAP-R-{idx:03d}",
            "来源": "规则缺口",
            "流程节点": node,
            "用例名称": f"验证规则「{item['规则名称']}」",
            "测试类型": item["测试类型"],
            "风险等级": risk_for_node(node, config),
            "前置条件": item["规则说明"],
            "测试步骤": "构造满足/不满足判定条件的数据并执行业务操作",
            "预期结果": "系统按规则计算、拦截或流转，错误提示明确，落库字段一致",
            "是否建议核心回归": "是",
        })
        idx += 1
    return rows


def is_core_case(case):
    core_mark = str(case.get("原始核心标记", "")).strip()
    level = str(case.get("等级", "")).strip()
    test_type = case.get("推荐测试类型", "")
    return (
        core_mark in ["1", "是", "Y", "y", "true", "True"]
        or level in ["P0", "P1", "高"]
        or test_type in ["金额计算", "状态流转", "规则校验", "数据一致性"]
    )


def build_core_cases(cases, gap_cases):
    rows = []
    for case in cases:
        if case["推荐流程节点"] == "待人工确认" or not is_core_case(case):
            continue
        platform = case.get("平台", "")
        rows.append({
            "来源": "历史用例复用",
            "用例ID": case["用例ID"],
            "用例名称": case["用例名称"],
            "流程节点": case["推荐流程节点"],
            "测试类型": case["推荐测试类型"],
            "风险等级": case["等级"] or "P1",
            "是否核心回归": "是",
            "自动化建议": "接口/服务层自动化优先" if "server" in platform.lower() or "服务端" in platform else "人工/端到端回归",
        })
    for gap in gap_cases:
        if gap["是否建议核心回归"] != "是":
            continue
        rows.append({
            "来源": gap["来源"],
            "用例ID": gap["用例编号"],
            "用例名称": gap["用例名称"],
            "流程节点": gap["流程节点"],
            "测试类型": gap["测试类型"],
            "风险等级": gap["风险等级"],
            "是否核心回归": "是",
            "自动化建议": "接口/服务层自动化优先",
        })
    return rows


def build_grouped_core(core_cases, config):
    rows = []
    for node in config["flowNodes"]:
        node_cases = [case for case in core_cases if case["流程节点"] == node["node"]]
        if not node_cases:
            rows.append({
                "流程节点": node["node"],
                "节点风险": node.get("risk", ""),
                "组内序号": "",
                "来源": "待补充",
                "用例ID": "",
                "用例名称": "该节点暂无核心回归候选，需人工补充",
                "测试类型": "",
                "风险等级": node.get("risk", ""),
                "自动化建议": "",
            })
            continue
        for idx, case in enumerate(node_cases, 1):
            rows.append({
                "流程节点": node["node"],
                "节点风险": node.get("risk", ""),
                "组内序号": idx,
                "来源": case["来源"],
                "用例ID": case["用例ID"],
                "用例名称": case["用例名称"],
                "测试类型": case["测试类型"],
                "风险等级": case["风险等级"],
                "自动化建议": case["自动化建议"],
            })
    return rows


def build_kb_ingestion_rows(config):
    return [
        {"层级": "领域", "字段/对象": "domain / displayName", "说明": "用领域承接业务边界，例如家装报价、量房、设计。", "处理建议": "作为用例知识库一级目录。"},
        {"层级": "流程节点", "字段/对象": "flowNode", "说明": "用核心流程节点组织用例，不再依赖历史模块弱标签。", "处理建议": "作为二级目录和筛选标签。"},
        {"层级": "测试依据", "字段/对象": "状态机/字段/规则/异常提示", "说明": "记录用例为什么存在，以及覆盖的是哪条业务逻辑。", "处理建议": "每条用例至少绑定一种测试依据。"},
        {"层级": "用例资产", "字段/对象": "历史用例/缺口用例/核心回归用例", "说明": "区分来源，避免 AI 生成用例直接混入正式全集。", "处理建议": "低置信度和缺口用例先进入评审池。"},
        {"层级": "评审状态", "字段/对象": "待确认/已确认/已废弃", "说明": "沉淀人工校准结果，后续反哺关键词、配置和模型提示。", "处理建议": "每轮领域地图生成后安排业务测试共同评审。"},
        {"层级": "自动化状态", "字段/对象": "未自动化/接口自动化/UI自动化/不适合自动化", "说明": "核心回归集需要逐步转自动化。", "处理建议": "优先自动化高风险接口、状态流转和计算规则。"},
        {"层级": "版本来源", "字段/对象": "knowledgeSourceVersion / originalCaseId", "说明": "记录知识库分析版本和历史用例 ID，保证可追溯。", "处理建议": "平台入库时设为必填字段。"},
        {"层级": "推荐落库结构", "字段/对象": f"{config.get('displayName', '')} -> 流程节点 -> 测试依据 -> 用例", "说明": "保持可查、可评审、可复用。", "处理建议": "不要只导入一张平铺表。"},
    ]


def build_outputs(cases, config):
    test_map = build_test_map(cases, config)
    state_matrix = build_state_matrix(cases, config)
    field_matrix = build_field_matrix(cases, config)
    rule_matrix = build_rule_matrix(cases, config)
    gap_cases = build_gap_cases(state_matrix, field_matrix, rule_matrix, config)
    core_cases = build_core_cases(cases, gap_cases)
    grouped_core = build_grouped_core(core_cases, config)
    kb_rows = build_kb_ingestion_rows(config)
    return test_map, state_matrix, field_matrix, rule_matrix, gap_cases, core_cases, grouped_core, kb_rows


def add_sheet(wb, name, rows, headers, note=""):
    ws = wb.create_sheet(name)
    title_fill = PatternFill("solid", fgColor="1F4E78")
    header_fill = PatternFill("solid", fgColor="D9EAF7")
    note_fill = PatternFill("solid", fgColor="EEF5FB")
    thin = Side(style="thin", color="D9E2EC")
    border = Border(left=thin, right=thin, top=thin, bottom=thin)
    ws.cell(1, 1, name).fill = title_fill
    ws.cell(1, 1).font = Font(color="FFFFFF", bold=True)
    ws.merge_cells(start_row=1, start_column=1, end_row=1, end_column=max(len(headers), 6))
    if note:
        ws.cell(2, 1, note).fill = note_fill
        ws.cell(2, 1).alignment = Alignment(wrap_text=True, vertical="top")
        ws.merge_cells(start_row=2, start_column=1, end_row=2, end_column=max(len(headers), 6))
    for c, h in enumerate(headers, 1):
        cell = ws.cell(4, c, h)
        cell.fill = header_fill
        cell.font = Font(bold=True)
        cell.border = border
    for r_idx, row in enumerate(rows, 5):
        for c_idx, h in enumerate(headers, 1):
            cell = ws.cell(r_idx, c_idx, row.get(h, ""))
            cell.alignment = Alignment(wrap_text=True, vertical="top")
            cell.border = border
    ws.freeze_panes = "A5"
    for c_idx, h in enumerate(headers, 1):
        width = 14
        if h in ["节点说明", "缺口结论", "前置条件", "测试步骤", "预期结果", "步骤", "规则说明", "建议测试点", "说明", "处理建议"]:
            width = 34
        elif h in ["用例名称", "功能点"]:
            width = 30
        elif h in ["流程节点", "推荐流程节点"]:
            width = 22
        ws.column_dimensions[get_column_letter(c_idx)].width = width


def style_overview(ws):
    title_fill = PatternFill("solid", fgColor="1F4E78")
    header_fill = PatternFill("solid", fgColor="D9EAF7")
    ws["A1"].fill = title_fill
    ws["A1"].font = Font(color="FFFFFF", bold=True)
    ws.merge_cells("A1:D1")
    ws["A3"].fill = header_fill
    ws["B3"].fill = header_fill
    ws["A3"].font = Font(bold=True)
    ws["B3"].font = Font(bold=True)
    ws.column_dimensions["A"].width = 22
    ws.column_dimensions["B"].width = 90
    for row in ws.iter_rows():
        for cell in row:
            cell.alignment = Alignment(wrap_text=True, vertical="top")


def write_workbook(path, cases, outputs, config, source_info):
    test_map, state_matrix, field_matrix, rule_matrix, gap_cases, core_cases, grouped_core, kb_rows = outputs
    wb = Workbook()
    ws = wb.active
    ws.title = "00_总览"
    ws["A1"] = f"{config.get('displayName', config.get('domain', '业务领域'))}测试地图 v1候选版"
    summary = [
        ("领域", config.get("displayName", "")),
        ("历史用例文件", source_info["cases_path"]),
        ("历史用例Sheet", source_info["case_sheet"]),
        ("历史用例总数", len(cases)),
        ("自动挂载", sum(1 for r in cases if r["处理建议"] == "自动挂载")),
        ("人工抽检", sum(1 for r in cases if r["处理建议"] == "人工抽检")),
        ("人工确认", sum(1 for r in cases if r["处理建议"] == "人工确认")),
        ("流程节点数", len(config["flowNodes"])),
        ("状态流转数", len(config["stateTransitions"])),
        ("字段数", len(config["fields"])),
        ("规则数", len(config["rules"])),
        ("缺口用例数", len(gap_cases)),
        ("核心回归候选数", len(core_cases)),
        ("知识库分析", source_info["knowledge_path"]),
        ("领域配置", source_info["config_path"] or "内置报价配置"),
        ("结论说明", "本工作簿为自动生成候选版，低置信度挂载、P0/P1缺口和规则理解不确定项需人工校准。"),
    ]
    ws.append([])
    ws.append(["指标", "数量/说明"])
    for item in summary:
        ws.append(list(item))
    style_overview(ws)

    add_sheet(wb, "01_测试地图", test_map, ["序号", "流程节点", "节点说明", "风险等级", "历史用例数"] + OUTPUT_TEST_TYPES + ["缺口结论", "缺口优先级"], "按状态机、字段、规则搭骨架，再把历史用例挂载到流程节点。")
    add_sheet(wb, "02_历史用例挂载", cases, ["用例ID", "用例名称", "功能点", "等级", "平台", "原始核心标记", "需求号", "推荐流程节点", "推荐测试类型", "关联状态链", "关联字段", "关联规则", "置信度", "匹配依据", "处理建议", "前置条件", "步骤", "预期结果"], "原始模块仅作为弱标签，低置信度需人工校准。")
    add_sheet(wb, "03_状态覆盖矩阵", state_matrix, ["状态链", "起始状态", "目标状态", "触发动作", "流转类型", "推荐节点", "匹配历史用例数", "覆盖状态", "建议用例"])
    add_sheet(wb, "04_字段覆盖矩阵", field_matrix, ["实体", "字段", "业务含义", "字段类型", "推荐节点", "匹配历史用例数", "覆盖状态", "建议测试点"])
    add_sheet(wb, "05_规则覆盖矩阵", rule_matrix, ["规则ID", "规则名称", "规则说明", "推荐节点", "测试类型", "匹配历史用例数", "覆盖状态", "建议测试点"])
    add_sheet(wb, "06_缺口用例", gap_cases, ["用例编号", "来源", "流程节点", "用例名称", "测试类型", "风险等级", "前置条件", "测试步骤", "预期结果", "是否建议核心回归"])
    add_sheet(wb, "07_核心回归集", core_cases, ["来源", "用例ID", "用例名称", "流程节点", "测试类型", "风险等级", "是否核心回归", "自动化建议"])
    add_sheet(wb, "08_核心回归_按流程节点", grouped_core, ["流程节点", "节点风险", "组内序号", "来源", "用例ID", "用例名称", "测试类型", "风险等级", "自动化建议"], "用于评审和执行回归时按流程节点领取。")
    add_sheet(wb, "09_用例知识库入库规范", kb_rows, ["层级", "字段/对象", "说明", "处理建议"], "建议按领域、流程节点、测试依据、用例资产分层入库。")
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    wb.save(path)


def write_json(path, outputs):
    keys = ["test_map", "state_matrix", "field_matrix", "rule_matrix", "gap_cases", "core_regression", "grouped_core_regression", "kb_ingestion"]
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(dict(zip(keys, outputs)), ensure_ascii=False, indent=2), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description="将历史测试用例和开发知识库分析结果生成业务领域用例地图。")
    parser.add_argument("--cases", required=True, help="历史测试用例 .xlsx")
    parser.add_argument("--knowledge", required=True, help="开发知识库分析 .txt/.md")
    parser.add_argument("--output", required=True, help="输出 .xlsx 路径")
    parser.add_argument("--domain", default="quote", help="领域编码，例如 quote、measurement、design")
    parser.add_argument("--config", help="领域配置 JSON。不传时默认使用报价配置。")
    parser.add_argument("--json-output", help="可选 JSON 输出路径")
    args = parser.parse_args()

    config = load_config(args.domain, args.config)
    cases, sheet_name, column_count = load_cases(args.cases, config)
    outputs = build_outputs(cases, config)
    source_info = {
        "cases_path": str(Path(args.cases).resolve()),
        "knowledge_path": str(Path(args.knowledge).resolve()),
        "config_path": str(Path(args.config).resolve()) if args.config else "",
        "case_sheet": f"{sheet_name}（{column_count}列）",
    }
    write_workbook(args.output, cases, outputs, config, source_info)
    if args.json_output:
        write_json(args.json_output, outputs)


if __name__ == "__main__":
    main()
