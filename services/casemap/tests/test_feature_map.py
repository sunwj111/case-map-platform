from pathlib import Path

from app.assets import CaseQueryService
from app.featuremap import FeatureMapAssembler
from app.knowledge import QuoteKnowledgeCatalog
from app.tech import ApiResolveService, FeatureApiMapCatalog, FlowNodeCatalog

DATA_DIR = Path(__file__).resolve().parents[1] / "data"


def test_known_feature_returns_complete_map(map_query):
    result = map_query.find_by_feature_key("家装/报价/金额计算与汇总/数量价汇总")
    assert result is not None
    assert result["meta"]["featureKey"] == "家装/报价/金额计算与汇总/数量价汇总"
    assert result["spine"]["domain"] == "家装"
    assert result["spine"]["app"] == "报价"
    assert result["spine"]["moduleName"] == ""
    assert result["spine"]["sceneName"] == "金额计算与汇总"
    assert result["spine"]["featureName"] == "数量价汇总"
    assert len(result["businessView"]["cases"]) == 3
    assert result["techView"]["apis"]
    assert result["summary"]["linkedCaseCount"] == 3
    assert result["meta"]["synthetic"] is False


def test_missing_assets_still_return_map_when_feature_exists(map_query):
    result = map_query.find_by_feature_key("家装/报价/参数解析与输入精度/参数必填校验")
    assert result is not None
    assert result["summary"]["linkedCaseCount"] == 0
    assert result["businessView"]["cases"] == []
    assert result["techView"]["apis"]


def test_synthetic_cost_audit_feature_returns_map(map_query):
    result = map_query.find_by_feature_key("家装/报价/造价审核/人工审核")
    assert result is not None
    assert result["meta"]["synthetic"] is True
    assert result["meta"]["mapId"].startswith("fm_synth_")
    assert result["meta"]["version"] == "1.0.0-synthetic"
    assert result["spine"]["sceneName"] == "造价审核"
    assert result["spine"]["featureName"] == "人工审核"
    assert result["spine"]["processName"] == "审核流程"
    assert result["spine"]["moduleName"] == ""
    assert len(result["businessView"]["cases"]) == 1
    assert result["businessView"]["cases"][0]["id"] == "CA-301"
    assert result["techView"]["apis"]


def test_same_system_feature_name_fallback_returns_map(map_query):
    result = map_query.find_by_feature_key("家装/报价/造价提交与审核/人工审核")
    assert result is not None
    assert result["meta"]["synthetic"] is True
    assert len(result["businessView"]["cases"]) == 1
    assert result["businessView"]["cases"][0]["id"] == "CA-301"


def test_tech_mapping_without_hierarchy_still_returns_map(map_query):
    result = map_query.find_by_feature_key("家装/报价/造价审核/转派")
    assert result is not None
    assert result["meta"]["synthetic"] is True
    assert result["businessView"]["cases"] == []
    assert any(api["path"] == "/costAudit/transform" for api in result["techView"]["apis"])


def test_unknown_feature_returns_empty(map_query):
    assert map_query.find_by_feature_key("家装/报价/金额计算与汇总/不存在功能点XYZ") is None


def test_assembler_fills_scripts_and_risk(hierarchy_store, case_store):
    assembler = FeatureMapAssembler(
        hierarchy_store,
        CaseQueryService(case_store),
        ApiResolveService(
            FeatureApiMapCatalog(DATA_DIR / "feature_api_map.json"),
            FlowNodeCatalog(DATA_DIR / "quote_flow_nodes.json"),
        ),
        QuoteKnowledgeCatalog(DATA_DIR / "quote_knowledge.json"),
    )
    result = assembler.assemble("家装/报价/金额计算与汇总/数量价汇总")
    business = result["businessView"]
    assert len(business["scripts"]) == 3
    assert all(script["status"] == "derived" for script in business["scripts"])
    assert all(script["source"] == "derived-from-official-case" for script in business["scripts"])
    assert any(script["name"] == "quotation_offer.json" for script in business["scripts"])
    assert result["summary"]["priorityDistribution"]["P0"] == 2
    assert result["summary"]["priorityDistribution"]["P1"] == 1
    assert any(rule["id"] == "R002" for rule in result["riskView"]["rules"])
    assert "quotations_record" in result["techView"]["tables"]
    assert "executions:unavailable" in result["meta"]["dataSources"]
