import json
from pathlib import Path

FIXTURE_DIR = Path(__file__).resolve().parent / "fixtures" / "quote_replay"


def _draft_hangs(produce_service, batch_body: dict) -> dict:
    batch = produce_service.create(batch_body)
    produce_service.import_cases(batch["id"], "cases.csv", (FIXTURE_DIR / "cases.csv").read_bytes())
    produce_service.import_knowledge(batch["id"], "knowledge.md", (FIXTURE_DIR / "knowledge.md").read_bytes())
    produce_service.extract_knowledge(batch["id"])
    produce_service.confirm_keywords(batch["id"], None)
    return produce_service.generate_draft(batch["id"])


def _hang_tuple(item: dict) -> tuple:
    return (
        item.get("originalCaseId"),
        item.get("scene"),
        item.get("feature"),
        item.get("confidence"),
        item.get("mountAdvice"),
        item.get("flowNode") or "",
    )


def test_quote_replay_keeps_auto_mount_and_feature_hangs(produce_service):
    expected = json.loads((FIXTURE_DIR / "expected.json").read_text(encoding="utf-8"))
    draft = _draft_hangs(produce_service, {
        "ziroom_domain": "家装",
        "system_ref": "报价",
        "module_name": "报价计算",
    })
    assert draft["stats"] == expected["stats"]
    actual = [_hang_tuple(item) for item in draft["cases"]]
    golden = [
        (
            item["originalCaseId"],
            item["scene"],
            item["feature"],
            item["confidence"],
            item["mountAdvice"],
            item.get("flowNode") or "",
        )
        for item in expected["cases"]
    ]
    assert actual == golden
    labeled_wrong = [
        item for item in draft["cases"]
        if item["originalCaseId"] in expected["labeledAutoMounted"]
        and item["mountAdvice"] != "自动挂载"
    ]
    assert labeled_wrong == []
    unlabeled = [item for item in draft["cases"] if str(item["originalCaseId"]).startswith("UL-")]
    assert all(item["mountAdvice"] == "人工确认" for item in unlabeled)
    assert all(item["feature"] != "未识别功能点" for item in unlabeled)


def test_quote_replay_platform_fields_do_not_change_hangs(produce_service):
    baseline = _draft_hangs(produce_service, {"domain": "家装", "system": "报价"})
    platform = _draft_hangs(produce_service, {
        "ziroom_domain": "家装",
        "system_ref": "报价",
        "module_name": "报价计算",
    })
    assert [_hang_tuple(item) for item in baseline["cases"]] == [_hang_tuple(item) for item in platform["cases"]]
    assert baseline["stats"] == platform["stats"]
