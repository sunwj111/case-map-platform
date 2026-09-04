#!/usr/bin/env python3
"""从现有报价材料生成最小交付四表（CSV，UTF-8 BOM，可直接用 Excel 打开）。"""

from __future__ import annotations

import csv
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "output" / "quote_min_delivery"


def write_csv(path: Path, headers: list[str], rows: list[list[str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(headers)
        writer.writerows(rows)


def load_quote_config() -> dict:
    return json.loads((ROOT / "configs" / "quote.json").read_text(encoding="utf-8"))


def parse_feature_api_map() -> list[dict]:
    text = (ROOT / "prototype" / "data" / "feature_api_map.js").read_text(encoding="utf-8")
    match = re.search(r"window\.__FEATURE_API_MAP__ = (\{.*?\});", text, re.S)
    if not match:
        raise RuntimeError("未解析到 feature_api_map")
    payload = match.group(1)
    payload = re.sub(r"(\w+):", r'"\1":', payload)
    payload = payload.replace("'", '"')
    # keys already quoted after naive replace may double-quote; use regex extract instead
    items = []
    for block in re.finditer(r"\{[^{}]+\}", text.split("items: [", 1)[1].rsplit("]", 1)[0]):
        chunk = block.group(0)
        method = re.search(r"method: '([^']+)'", chunk)
        paths = re.findall(r"'(/[^']+)'", chunk)
        primary = re.search(r"primaryNode: '([^']+)'", chunk)
        nodes = re.search(r"nodes: \[([^\]]+)\]", chunk)
        features = re.search(r"features: \[([^\]]+)\]", chunk)
        relation = re.search(r"relation: '([^']+)'", chunk)
        controller = re.search(r"controller: '([^']+)'", chunk)
        if not method or not paths or not primary:
            continue
        node_list = re.findall(r"'([^']+)'", nodes.group(1)) if nodes else []
        feature_list = re.findall(r"'([^']+)'", features.group(1)) if features else []
        items.append({
            "method": method.group(1),
            "paths": paths,
            "primaryNode": primary.group(1),
            "nodes": node_list,
            "features": feature_list,
            "relation": relation.group(1) if relation else "",
            "controller": controller.group(1) if controller else "",
        })
    return items


def parse_sample_cases() -> list[dict]:
    text = (ROOT / "prototype" / "data" / "feature_map_samples.js").read_text(encoding="utf-8")
    cases = []
    current_key = ""
    current_feature = ""
    current_node = ""
    for raw_line in text.splitlines():
        key_match = re.search(r"'([^']+/[^']+)'\s*:", raw_line)
        if key_match and raw_line.strip().startswith("'家装"):
            current_key = key_match.group(1)
            parts = current_key.split("/")
            current_node = parts[2] if len(parts) > 2 else ""
            current_feature = parts[3] if len(parts) > 3 else ""
        feature_name = re.search(r"featureName: '([^']+)'", raw_line)
        if feature_name and "neighborFeatures" not in raw_line:
            current_feature = feature_name.group(1)
        scene_name = re.search(r"sceneName: '([^']+)'", raw_line)
        if scene_name:
            current_node = scene_name.group(1)
        case_match = re.search(
            r"id: '([^']+)', name: '([^']+)', priority: '([^']+)', testScenario: '([^']+)', confidence: (\d+), mountAdvice: '([^']+)', api: '([^']+)'",
            raw_line,
        )
        if case_match:
            cases.append({
                "case_id": case_match.group(1),
                "case_name": case_match.group(2),
                "priority": case_match.group(3),
                "scenario": case_match.group(4),
                "confidence": case_match.group(5),
                "advice": case_match.group(6),
                "api": case_match.group(7),
                "feature_key": current_key,
                "feature": current_feature,
                "node": current_node,
            })
    return cases


def build_node_edges(config: dict) -> list[list[str]]:
    rows = [
        ["家装", "报价", "报价触发与报价单生成", "报价场景识别与价格来源", "主流程", "强", "quote.json 节点顺序", "待确认"],
        ["家装", "报价", "报价场景识别与价格来源", "参数解析与输入精度", "主流程", "强", "quote.json 节点顺序", "待确认"],
        ["家装", "报价", "参数解析与输入精度", "物料处理与自动带出", "主流程", "强", "quote.json 节点顺序", "待确认"],
        ["家装", "报价", "物料处理与自动带出", "金额计算与汇总", "主流程", "强", "quote.json 节点顺序", "待确认"],
        ["家装", "报价", "金额计算与汇总", "造价单生成与明细落库", "主流程", "强", "quote.json 节点顺序", "待确认"],
        ["家装", "报价", "造价单生成与明细落库", "造价提交与审核", "主流程", "强", "quote.json 节点顺序", "待确认"],
        ["家装", "报价", "造价提交与审核", "实勘审核", "分支", "弱", "domain_panorama 业务边", "待确认"],
        ["家装", "报价", "造价提交与审核", "申诉审核", "分支", "弱", "domain_panorama 业务边", "待确认"],
        ["家装", "报价", "造价提交与审核", "生命周期与外部联动", "分支", "强", "domain_panorama 业务边", "待确认"],
        ["家装", "报价", "金额计算与汇总", "前端展示与金额明细", "分支", "弱", "domain_panorama 业务边", "待确认"],
        ["家装", "报价", "参数解析与输入精度", "前端展示与金额明细", "弱依赖", "弱", "domain_panorama 业务边", "待确认"],
    ]
    for item in config.get("stateTransitions", []):
        rows.append([
            "家装",
            "报价",
            item["node"],
            item["node"],
            "状态机:" + item["type"],
            "强" if item["type"] in ("正向", "异常") else "中",
            item["chain"] + " " + item["source"] + " → " + item["target"] + "（" + item["action"] + "）",
            "已配置",
        ])
    return rows


def build_feature_api(items: list[dict]) -> list[list[str]]:
    rows = []
    for item in items:
        for path in item["paths"]:
            features = item["features"] or [""]
            for feature in features:
                rows.append([
                    "家装",
                    "报价",
                    item["primaryNode"],
                    feature,
                    item["method"],
                    path,
                    item["relation"],
                    item["controller"],
                    "；".join(item["nodes"]),
                    "feature_api_map / 接口清单第五节",
                    "待研发确认主接口",
                ])
    return rows


def build_case_rows(cases: list[dict]) -> list[list[str]]:
    rows = []
    for item in cases:
        inherit = "用例直连" if item["api"] else "继承功能点主接口"
        rows.append([
            item["case_id"],
            item["case_name"],
            item["priority"],
            "家装",
            "报价",
            item["node"],
            item["feature"],
            item["feature_key"],
            item["scenario"],
            item["api"],
            inherit,
            item["confidence"],
            item["advice"],
            "feature_map_samples.js",
            "样例" if item["case_id"].startswith("TC-") else "待替换为历史导出",
        ])
    rows.append([
        "（待填）",
        "请将质保平台历史用例导出放到 input/quote/历史用例.xlsx",
        "",
        "家装",
        "报价",
        "",
        "",
        "",
        "",
        "",
        "无接口则继承功能点主接口",
        "",
        "待挂载",
        "历史用例.xlsx",
        "待提供",
    ])
    return rows


def build_api_downstream() -> list[list[str]]:
    return [
        ["POST", "/quotation/offer", "BOM", "Dubbo BomOuterRpcService.findBomParams", "Dubbo", "同步", "强", "报价触发与报价单生成", "接口清单-下游依赖", "待确认"],
        ["POST", "/quotation/offer", "BOM", "Dubbo BomOuterRpcService.findBomStructureAddStandSignAndStock", "Dubbo", "同步", "强", "物料处理与自动带出", "接口清单-下游依赖", "待确认"],
        ["POST", "/quotation/offer", "房源", "HTTP HouseInfoAdaptor.findHouseInfo", "HTTP", "同步", "强", "报价场景识别与价格来源", "接口清单-下游依赖", "待确认"],
        ["POST", "/quotation/offer", "人员", "HTTP PersonAdaptor.getPersonInfo", "HTTP", "同步", "弱", "报价触发与报价单生成", "接口清单-下游依赖", "待确认"],
        ["POST", "/quotation/offer", "报价库", "MongoDB quotations.saveQuotation", "DB", "同步", "强", "报价触发与报价单生成", "接口清单-下游依赖", "待确认"],
        ["POST", "/quotation/offer", "造价库", "MySQL jz_design_cost.saveDesignCost", "DB", "同步", "强", "造价单生成与明细落库", "接口清单-下游依赖", "待确认"],
        ["POST", "/quotation/offer", "实勘审核", "initQuotationsReview", "本地", "同步", "弱", "实勘审核", "全托管 ZO 场景", "待确认"],
        ["POST", "/quotation/bim/offer", "BOM", "HTTP BomInfoAdaptor.findNBomGoodsFilterStandWithStock", "HTTP", "同步", "强", "物料处理与自动带出", "接口清单-下游依赖", "待确认"],
        ["POST", "/quotation/bim/offer", "报价规则", "OfferRuleSupportService.findAllRulesByCurrentScene", "本地", "同步", "强", "报价场景识别与价格来源", "接口清单-下游依赖", "待确认"],
        ["POST", "/quotation/createDesignCost", "造价库", "MySQL jz_design_cost / jz_design_cost_detail", "DB", "同步", "强", "造价单生成与明细落库", "接口清单-下游依赖", "待确认"],
        ["POST", "/quotation/submitDesignCost", "造价库", "designCostDao.submitDesignCost", "DB", "同步", "强", "造价提交与审核", "接口清单-下游依赖", "待确认"],
        ["POST", "/quotation/submitDesignCost", "MQ", "ex_design_cost / design_cost_routing_key", "MQ", "异步", "强", "造价提交与审核", "第三节下游汇总", "待确认"],
        ["POST", "/quotation/submitDesignCost", "钉钉", "dingUtil.sendQuotationErrorMsg", "HTTP", "异步", "弱", "造价提交与审核", "接口清单-下游依赖", "待确认"],
        ["POST", "/quotation/confirmDesignCost", "人员", "HTTP PersonAdaptor.getPersonInfo", "HTTP", "同步", "弱", "造价提交与审核", "接口清单-下游依赖", "待确认"],
        ["POST", "/quotation/confirmDesignCost", "房源品质", "HTTP HouseQualityAdaptor.batchGradeByOppIdsAndSource", "HTTP", "同步", "弱", "造价提交与审核", "接口清单-下游依赖", "待确认"],
        ["POST", "/quotation/confirmDesignCost", "MQ", "ex_design_cost 审核结果", "MQ", "异步", "强", "生命周期与外部联动", "第三节下游汇总", "待确认"],
        ["POST", "/quotation/confirmDesignCost", "延迟消息", "Foxy DESIGN_COST_EXPIRE_KEY", "MQ", "异步", "强", "生命周期与外部联动", "第三节下游汇总", "待确认"],
        ["POST", "/quotation/confirmDesignCost", "库存", "EventBusHandler 预占库存", "本地/RPC", "异步", "强", "生命周期与外部联动", "接口清单-下游依赖", "待确认"],
        ["POST", "/quotation/reviewSubmit", "MQ", "EX_settle_quotation_review", "MQ", "异步", "强", "实勘审核", "第三节下游汇总", "待确认"],
        ["POST", "/quotation/reviewSubmit", "报价生成", "调整通过时回调用 submitQuotations / renewOffer", "HTTP/本地", "同步", "强", "报价触发与报价单生成", "接口清单-下游依赖", "待确认"],
    ]


def main() -> None:
    config = load_quote_config()
    api_items = parse_feature_api_map()
    cases = parse_sample_cases()

    write_csv(
        OUT / "01_节点上下游.csv",
        ["领域", "应用", "上游节点", "下游节点", "关系类型", "强弱", "依据", "确认状态"],
        build_node_edges(config),
    )
    write_csv(
        OUT / "02_功能点_接口.csv",
        ["领域", "应用", "主归属流程节点", "功能点", "方法", "路径", "关系类型", "Controller", "关联流程节点", "来源", "确认状态"],
        build_feature_api(api_items),
    )
    write_csv(
        OUT / "03_用例_功能点_接口.csv",
        ["用例ID", "用例名称", "优先级", "领域", "应用", "流程节点", "功能点", "featureKey", "测试场景", "关联接口", "接口来源", "置信度", "处理建议", "数据来源", "确认状态"],
        build_case_rows(cases),
    )
    write_csv(
        OUT / "04_接口下游.csv",
        ["方法", "本接口路径", "下游系统", "下游接口或资源", "调用方式", "同步异步", "强弱", "关联流程节点", "来源", "确认状态"],
        build_api_downstream(),
    )
    print("generated", OUT)


if __name__ == "__main__":
    main()
