import pytest

from app.assets import CaseQueryService


def test_only_returns_confirmed_and_not_archived(case_store):
    result = CaseQueryService(case_store).query(None, "数量价汇总", "金额计算与汇总")
    ids = {item["id"] for item in result["items"]}
    assert result["total"] == 3
    assert {"TC-001", "TC-002", "TC-003"} <= ids
    assert "TC-004" not in ids
    assert "TC-005" not in ids
    for item in result["items"]:
        assert item["status"] == "已确认"
        assert item.get("lifecycle") != "已归档"


def test_filter_by_feature_and_test_scenario(case_store):
    result = CaseQueryService(case_store).query(None, "数量价汇总", "边界校验")
    assert result["total"] == 1
    assert result["items"][0]["id"] == "TC-003"


def test_filter_by_feature_key(case_store):
    result = CaseQueryService(case_store).query("家装/报价/金额计算与汇总/固定价汇总", None, None)
    ids = {item["id"] for item in result["items"]}
    assert result["total"] == 2
    assert {"TC-101", "TC-102"} <= ids


def test_discarded_case_excluded(case_store):
    result = CaseQueryService(case_store).query(None, "自动审核判定", None)
    ids = {item["id"] for item in result["items"]}
    assert "TC-201" in ids
    assert "TC-202" not in ids


def test_missing_feature_rejected(case_store):
    with pytest.raises(ValueError):
        CaseQueryService(case_store).query(None, None, "金额计算与汇总")


def test_query_does_not_fallback_across_scenes(case_store):
    result = CaseQueryService(case_store).query("家装/报价/造价提交与审核/人工审核", None, None)
    assert result["total"] == 0


def test_filter_by_module_name(case_store):
    seeded = CaseQueryService(case_store).query(None, "数量价汇总", None, "报价计算")
    assert seeded["total"] == 0
    assert seeded["moduleName"] == "报价计算"

    case_store.publish([{
        "id": "CA-MODULE-001",
        "name": "模块筛选用例",
        "priority": "P1",
        "testScenario": "金额计算与汇总",
        "featureName": "数量价汇总",
        "sceneName": "金额计算与汇总",
        "featureKey": "家装/报价/金额计算与汇总/数量价汇总",
        "status": "已确认",
        "lifecycle": "已发布",
        "moduleName": "报价计算",
    }])
    matched = CaseQueryService(case_store).query(None, "数量价汇总", None, "报价计算")
    assert matched["total"] == 1
    assert matched["items"][0]["id"] == "CA-MODULE-001"
    unfiltered = CaseQueryService(case_store).query(None, "数量价汇总", None)
    assert unfiltered["moduleName"] is None
    assert unfiltered["total"] >= 4


def test_query_for_map_uses_exact_feature_key_first(case_store):
    result = CaseQueryService(case_store).query_for_map("家装/报价/造价审核/人工审核")
    assert result["total"] == 1
    assert result["items"][0]["id"] == "CA-301"


def test_query_for_map_falls_back_to_same_system_feature_name(case_store):
    result = CaseQueryService(case_store).query_for_map("家装/报价/造价提交与审核/人工审核")
    assert result["total"] == 1
    assert result["items"][0]["id"] == "CA-301"
