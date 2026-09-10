from pathlib import Path

from app.knowledge import QuoteKnowledgeCatalog, to_table_name

DATA_DIR = Path(__file__).resolve().parents[1] / "data"


def test_matches_quantity_price_rules_and_amount_tables():
    catalog = QuoteKnowledgeCatalog(DATA_DIR / "quote_knowledge.json")
    rules = catalog.find_rules("金额计算与汇总", "数量价汇总", ["金额计算与汇总"])
    rule_ids = {rule["id"] for rule in rules}
    assert "R002" in rule_ids
    assert "R014" in rule_ids
    assert "R001" not in rule_ids
    tables = catalog.find_tables("金额计算与汇总", ["金额计算与汇总"])
    assert "quotations_record" in tables
    assert "offer_rule" in tables
    assert to_table_name("QuotationsRecord") == "quotations_record"
