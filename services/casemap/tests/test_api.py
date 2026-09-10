from app.feature_key import encode_for_url


KNOWN_KEY = "家装/报价/金额计算与汇总/数量价汇总"


def test_health(client):
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["ok"] is True
    assert body["stack"] == "python-fastapi"
    assert body["nodes"] > 0


def test_feature_map_for_known_feature(client):
    response = client.get("/api/v1/feature-maps/" + KNOWN_KEY)
    assert response.status_code == 200
    body = response.json()
    assert body["meta"]["featureKey"] == KNOWN_KEY
    assert body["spine"]["featureName"] == "数量价汇总"
    assert body["summary"]["linkedCaseCount"] == 3
    assert body["meta"]["synthetic"] is False


def test_feature_map_for_url_encoded_key(client):
    response = client.get("/api/v1/feature-maps/" + encode_for_url(KNOWN_KEY))
    assert response.status_code == 200
    assert response.json()["meta"]["featureKey"] == KNOWN_KEY


def test_feature_map_not_found(client):
    missing = "家装/报价/金额计算与汇总/不存在功能点XYZ"
    response = client.get("/api/v1/feature-maps/" + missing)
    assert response.status_code == 404
    assert response.json()["detail"] == "功能点不存在：" + missing


def test_feature_map_bad_request(client):
    response = client.get("/api/v1/feature-maps/只有两段/功能点")
    assert response.status_code == 400
    assert "detail" in response.json()


def test_synthetic_map(client):
    response = client.get("/api/v1/feature-maps/家装/报价/造价审核/人工审核")
    assert response.status_code == 200
    body = response.json()
    assert body["meta"]["synthetic"] is True
    assert body["businessView"]["cases"][0]["id"] == "CA-301"


def test_create_produce_batch(client):
    response = client.post("/api/v1/produce/batches", json={
        "domain": "家装",
        "system": "报价",
        "mode": "standard",
    })
    assert response.status_code == 201
    body = response.json()
    assert body["mode"] == "standard"
    assert body["knowledgeRequired"] is True
    assert body["missingInputs"] == ["cases", "knowledge"]
    assert body["moduleName"] == ""


def test_create_produce_batch_with_module_name(client):
    response = client.post("/api/v1/produce/batches", json={
        "domain": "家装",
        "system": "报价",
        "moduleName": "报价计算",
        "mode": "standard",
    })
    assert response.status_code == 201
    assert response.json()["moduleName"] == "报价计算"


def test_create_produce_batch_with_platform_aliases(client):
    response = client.post("/api/v1/produce/batches", json={
        "ziroom_domain": "收房",
        "system_ref": "租住系统",
        "module_name": "签约",
        "mode": "standard",
    })
    assert response.status_code == 201
    body = response.json()
    assert body["domain"] == "收房"
    assert body["system"] == "租住系统"
    assert body["moduleName"] == "签约"
    assert body["system_ref"] == "租住系统"


def test_list_cases_accepts_platform_module_name(client):
    filtered = client.get("/api/v1/cases", params={"feature": "数量价汇总", "module_name": "报价计算"})
    assert filtered.status_code == 200
    assert filtered.json()["moduleName"] == "报价计算"
    assert filtered.json()["total"] == 0


def test_overview_cascade_accepts_platform_aliases(client):
    response = client.get("/api/v1/overview/cascade", params={
        "ziroom_domain": "家装",
        "system_ref": "报价",
    })
    assert response.status_code == 200
    modules = response.json()["modules"]
    assert modules
    assert modules[0]["name"] == "未填写模块"


def test_list_cases_optional_module_name(client):
    unfiltered = client.get("/api/v1/cases", params={"feature": "数量价汇总"})
    assert unfiltered.status_code == 200
    assert unfiltered.json()["moduleName"] is None
    filtered = client.get("/api/v1/cases", params={"feature": "数量价汇总", "moduleName": "报价计算"})
    assert filtered.status_code == 200
    assert filtered.json()["moduleName"] == "报价计算"
    assert filtered.json()["total"] == 0


def test_unknown_batch_not_found(client):
    response = client.get("/api/v1/produce/batches/missing-batch")
    assert response.status_code == 404
    assert response.json()["detail"] == "导入批次不存在：missing-batch"


def test_static_pages(client):
    assert client.get("/").status_code == 200
    assert client.get("/feature-map.html").status_code == 200
    assert client.get("/produce.html").status_code == 200


def test_overview_tree_api(client):
    response = client.get("/api/v1/overview/tree")
    assert response.status_code == 200
    body = response.json()
    assert body["axis"] == ["domain", "system", "module"]
    home = next(node for node in body["nodes"] if node["name"] == "家装")
    quote = next(node for node in home["children"] if node["name"] == "报价")
    assert quote["children"][0]["level"] == "module"


def test_hierarchy_tree_still_uses_scene_feature(client):
    response = client.get("/api/v1/hierarchy/tree")
    assert response.status_code == 200
    home = next(node for node in response.json() if node["name"] == "家装")
    quote = next(node for node in home["children"] if node["name"] == "报价")
    assert quote["children"][0]["level"] == "scene"
