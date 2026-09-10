from app.overview import UNNAMED_MODULE_LABEL, OverviewTreeService


def test_overview_tree_groups_seed_cases_under_unnamed_module(hierarchy_store, case_store):
    result = OverviewTreeService(hierarchy_store, case_store).as_tree()
    assert result["axis"] == ["domain", "system", "module"]
    home = next(node for node in result["nodes"] if node["name"] == "家装")
    quote = next(node for node in home["children"] if node["name"] == "报价")
    unnamed = next(node for node in quote["children"] if node["moduleName"] == "")
    assert unnamed["name"] == UNNAMED_MODULE_LABEL
    assert unnamed["caseCount"] == home["caseCount"]
    scene = next(node for node in unnamed["children"] if node["name"] == "金额计算与汇总")
    feature = next(node for node in scene["children"] if node["name"] == "数量价汇总")
    assert feature["featureKey"] == "家装/报价/金额计算与汇总/数量价汇总"
    assert feature["caseCount"] == 3
    assert {child["id"] for child in feature["children"]} == {"TC-001", "TC-002", "TC-003"}


def test_overview_keeps_catalog_systems_without_cases(hierarchy_store, case_store):
    result = OverviewTreeService(hierarchy_store, case_store).as_tree()
    shoufang = next(node for node in result["nodes"] if node["name"] == "收房")
    assert shoufang["caseCount"] == 0
    assert shoufang["children"] == []


def test_overview_cascade_lists_modules(hierarchy_store, case_store):
    service = OverviewTreeService(hierarchy_store, case_store)
    empty = service.cascade(None, None)
    assert "家装" in empty["domains"]
    assert empty["systems"] == []
    assert empty["modules"] == []
    by_domain = service.cascade("家装", None)
    assert "报价" in by_domain["systems"]
    by_system = service.cascade("家装", "报价")
    assert by_system["modules"][0]["name"] == UNNAMED_MODULE_LABEL
    assert by_system["modules"][0]["moduleName"] == ""
    assert by_system["modules"][0]["caseCount"] > 0


def test_published_module_appears_as_sibling(hierarchy_store, case_store):
    case_store.publish([{
        "id": "CA-MODULE-TREE",
        "name": "模块树用例",
        "priority": "P1",
        "testScenario": "金额计算与汇总",
        "featureName": "数量价汇总",
        "sceneName": "金额计算与汇总",
        "featureKey": "家装/报价/金额计算与汇总/数量价汇总",
        "status": "已确认",
        "lifecycle": "已发布",
        "moduleName": "报价计算",
    }])
    quote = next(
        system
        for domain in OverviewTreeService(hierarchy_store, case_store).as_tree()["nodes"]
        if domain["name"] == "家装"
        for system in domain["children"]
        if system["name"] == "报价"
    )
    names = [node["name"] for node in quote["children"]]
    assert names == ["报价计算", UNNAMED_MODULE_LABEL]
    named = quote["children"][0]
    assert named["moduleName"] == "报价计算"
    assert named["caseCount"] == 1
