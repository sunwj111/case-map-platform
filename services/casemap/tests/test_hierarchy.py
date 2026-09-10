from app.hierarchy import HierarchyStore, quote_seed_nodes


def test_seed_and_cascade(hierarchy_store: HierarchyStore):
    assert hierarchy_store.get_by_feature_key("家装/报价/金额计算与汇总/数量价汇总") is not None
    assert "家装" in hierarchy_store.cascade_options(None, None, None)["domains"]
    assert "报价" in hierarchy_store.cascade_options("家装", None, None)["systems"]
    assert "金额计算与汇总" in hierarchy_store.cascade_options("家装", "报价", None)["scenes"]
    features = hierarchy_store.cascade_options("家装", "报价", "金额计算与汇总")["features"]
    assert any(item["name"] == "数量价汇总" for item in features)


def test_create_feature_and_rename_cascade(hierarchy_store: HierarchyStore):
    scene = next(node for node in hierarchy_store.list_nodes("scene") if node["name"] == "金额计算与汇总")
    created = hierarchy_store.create({
        "level": "feature",
        "name": "租赁价汇总",
        "parentId": scene["id"],
    })
    assert created["featureKey"] == "家装/报价/金额计算与汇总/租赁价汇总"
    hierarchy_store.update(scene["id"], {"name": "金额计算"})
    renamed = hierarchy_store.get(created["id"])
    assert renamed["featureKey"] == "家装/报价/金额计算/租赁价汇总"


def test_replace_all_reloads_seed(tmp_path):
    store = HierarchyStore(tmp_path / "store.json")
    store.replace_all(quote_seed_nodes())
    reloaded = HierarchyStore(tmp_path / "store.json")
    assert reloaded.get_by_feature_key("家装/报价/金额计算与汇总/数量价汇总") is not None
