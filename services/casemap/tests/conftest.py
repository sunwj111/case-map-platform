from __future__ import annotations

from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.assets import CaseAssetStore, CaseQueryService
from app.featuremap import FeatureMapAssembler, FeatureMapQueryService
from app.hierarchy import HierarchyStore, quote_seed_nodes
from app.knowledge import QuoteKnowledgeCatalog
from app.main import build_app
from app.produce import ProduceService
from app.tech import ApiResolveService, FeatureApiMapCatalog, FlowNodeCatalog

DATA_DIR = Path(__file__).resolve().parents[1] / "data"


@pytest.fixture
def runtime_dir(tmp_path: Path) -> Path:
    return tmp_path / "runtime"


@pytest.fixture
def client(runtime_dir: Path) -> TestClient:
    return TestClient(build_app(data_dir=DATA_DIR, runtime_dir=runtime_dir))


@pytest.fixture
def hierarchy_store(runtime_dir: Path) -> HierarchyStore:
    store = HierarchyStore(runtime_dir / "hierarchy_store.json")
    store.replace_all(quote_seed_nodes())
    return store


@pytest.fixture
def case_store(runtime_dir: Path) -> CaseAssetStore:
    return CaseAssetStore(DATA_DIR / "case_assets.json", runtime_dir / "produce" / "case_assets.json")


@pytest.fixture
def map_query(hierarchy_store: HierarchyStore, case_store: CaseAssetStore) -> FeatureMapQueryService:
    case_query = CaseQueryService(case_store)
    api_map = FeatureApiMapCatalog(DATA_DIR / "feature_api_map.json")
    assembler = FeatureMapAssembler(
        hierarchy_store,
        case_query,
        ApiResolveService(api_map, FlowNodeCatalog(DATA_DIR / "quote_flow_nodes.json")),
        QuoteKnowledgeCatalog(DATA_DIR / "quote_knowledge.json"),
    )
    return FeatureMapQueryService(hierarchy_store, case_query, api_map, assembler)


@pytest.fixture
def produce_service(hierarchy_store: HierarchyStore, case_store: CaseAssetStore, runtime_dir: Path) -> ProduceService:
    return ProduceService(
        hierarchy_store,
        case_store,
        runtime_dir / "produce" / "import_batches.json",
        runtime_dir / "produce" / "review_queue.json",
    )
