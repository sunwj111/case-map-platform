from __future__ import annotations

import json
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, File, Request, UploadFile
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from fastapi import FastAPI, File, Request, UploadFile
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from app.assets import CaseAssetStore, CaseQueryService
from app.errors import FeatureKeyError, NotFoundError
from app.feature_key import EXAMPLE_QUOTE_KEYS, decode_from_url, encode_for_url, from_path_variable, join, parse
from app.featuremap import FeatureMapAssembler, FeatureMapQueryService
from app.hierarchy import HierarchyStore, quote_seed_nodes
from app.knowledge import QuoteKnowledgeCatalog
from app.overview import OverviewTreeService
from app.platform_fields import pick_label
from app.produce import ProduceService
from app.tech import ApiResolveService, FeatureApiMapCatalog, FlowNodeCatalog

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_DATA_DIR = ROOT / "data"
DEFAULT_RUNTIME_DIR = ROOT / "runtime"
DEFAULT_STATIC_DIR = ROOT / "static"


class EncodeBody(BaseModel):
    domain: str
    system: str
    scene: str
    feature: str


class DecodeBody(BaseModel):
    encoded: Optional[str] = None
    featureKey: Optional[str] = None


class CreateBatchBody(BaseModel):
    domain: Optional[str] = None
    system: Optional[str] = None
    moduleName: Optional[str] = None
    ziroom_domain: Optional[str] = None
    system_ref: Optional[str] = None
    module_name: Optional[str] = None
    process_node: Optional[str] = None
    processNode: Optional[str] = None
    mode: str = "standard"


class KnowledgeTextBody(BaseModel):
    sourceName: Optional[str] = None
    text: str


class ConfirmKeywordsBody(BaseModel):
    scenes: list[str] = []
    features: list[str] = []
    rules: list[str] = []
    nodes: list[str] = []


class BatchConfirmBody(BaseModel):
    reviewIds: list[str]
    operator: Optional[str] = None
    comment: Optional[str] = None


async def _optional_json(request: Request) -> Optional[dict]:
    raw = await request.body()
    if not raw.strip():
        return None
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as error:
        raise ValueError("请求体不是有效 JSON") from error
    return payload if isinstance(payload, dict) else None


def build_app(
    data_dir: Optional[Path] = None,
    runtime_dir: Optional[Path] = None,
    static_dir: Optional[Path] = None,
) -> FastAPI:
    data_dir = data_dir or DEFAULT_DATA_DIR
    runtime_dir = runtime_dir or DEFAULT_RUNTIME_DIR
    static_dir = DEFAULT_STATIC_DIR if static_dir is None else static_dir

    hierarchy_store = HierarchyStore(runtime_dir / "hierarchy_store.json")
    case_store = CaseAssetStore(data_dir / "case_assets.json", runtime_dir / "produce" / "case_assets.json")
    case_query = CaseQueryService(case_store)
    api_map = FeatureApiMapCatalog(data_dir / "feature_api_map.json")
    flow_nodes = FlowNodeCatalog(data_dir / "quote_flow_nodes.json")
    api_resolve = ApiResolveService(api_map, flow_nodes)
    knowledge = QuoteKnowledgeCatalog(data_dir / "quote_knowledge.json")
    assembler = FeatureMapAssembler(hierarchy_store, case_query, api_resolve, knowledge)
    map_query = FeatureMapQueryService(hierarchy_store, case_query, api_map, assembler)
    overview = OverviewTreeService(hierarchy_store, case_store)
    produce = ProduceService(
        hierarchy_store,
        case_store,
        runtime_dir / "produce" / "import_batches.json",
        runtime_dir / "produce" / "review_queue.json",
    )

    app = FastAPI(title="Case Map Python Service", version="0.1.0")

    @app.exception_handler(FeatureKeyError)
    async def feature_key_error(_request: Request, error: FeatureKeyError) -> JSONResponse:
        return JSONResponse({"detail": str(error)}, status_code=400)

    @app.exception_handler(NotFoundError)
    async def not_found_error(_request: Request, error: NotFoundError) -> JSONResponse:
        return JSONResponse({"detail": str(error)}, status_code=404)

    @app.exception_handler(ValueError)
    async def value_error(_request: Request, error: ValueError) -> JSONResponse:
        return JSONResponse({"detail": str(error)}, status_code=400)

    @app.get("/health")
    def health() -> dict:
        return {"ok": True, "service": "hierarchy", "stack": "python-fastapi", "nodes": hierarchy_store.size()}

    @app.get("/api/v1/feature-keys/examples")
    def examples() -> dict:
        items = []
        for key in EXAMPLE_QUOTE_KEYS:
            parts = parse(key)
            items.append({"featureKey": key, "parts": parts.as_list(), "urlEncoded": encode_for_url(key)})
        return {"rule": "领域/系统/场景/功能点", "separator": "/", "examples": items}

    @app.post("/api/v1/feature-keys/encode")
    def encode_key(body: EncodeBody) -> dict:
        key = join(body.domain, body.system, body.scene, body.feature)
        return {"featureKey": key, "urlEncoded": encode_for_url(key)}

    @app.post("/api/v1/feature-keys/decode")
    def decode_key(body: DecodeBody) -> dict:
        if body.encoded:
            key = decode_from_url(body.encoded)
        elif body.featureKey:
            key = parse(body.featureKey).to_key()
        else:
            raise FeatureKeyError("请提供 featureKey 或 encoded")
        parts = parse(key)
        return {
            "featureKey": key,
            "domain": parts.domain,
            "system": parts.system,
            "scene": parts.scene,
            "feature": parts.feature,
        }

    @app.get("/api/v1/hierarchy/tree")
    def tree(includeDisabled: bool = False) -> list:
        return hierarchy_store.as_tree(includeDisabled)

    @app.get("/api/v1/hierarchy/nodes")
    def nodes(
        level: Optional[str] = None,
        parentId: Optional[str] = None,
        status: Optional[str] = None,
        includeDisabled: bool = False,
    ) -> list:
        return hierarchy_store.list_nodes(level, parentId, status, includeDisabled)

    @app.get("/api/v1/hierarchy/cascade")
    def cascade(domain: Optional[str] = None, system: Optional[str] = None, scene: Optional[str] = None) -> dict:
        return hierarchy_store.cascade_options(domain, system, scene)

    @app.get("/api/v1/overview/tree")
    def overview_tree() -> dict:
        return overview.as_tree()

    @app.get("/api/v1/overview/cascade")
    def overview_cascade(
        domain: Optional[str] = None,
        system: Optional[str] = None,
        ziroom_domain: Optional[str] = None,
        system_ref: Optional[str] = None,
    ) -> dict:
        return overview.cascade(
            pick_label(domain, ziroom_domain, "领域") or None,
            pick_label(system, system_ref, "系统") or None,
        )

    @app.get("/api/v1/hierarchy/features/{feature_key:path}")
    def feature_node(feature_key: str) -> dict:
        key = from_path_variable(feature_key)
        node = hierarchy_store.get_by_feature_key(key)
        if node is None:
            raise NotFoundError("功能点不存在：" + key)
        return node

    @app.post("/api/v1/hierarchy/nodes", status_code=201)
    def create_node(body: dict) -> dict:
        return hierarchy_store.create(body)

    @app.patch("/api/v1/hierarchy/nodes/{node_id}")
    def update_node(node_id: str, body: dict) -> dict:
        return hierarchy_store.update(node_id, body)

    @app.post("/api/v1/hierarchy/nodes/{node_id}/disable")
    def disable_node(node_id: str) -> dict:
        return hierarchy_store.disable(node_id)

    @app.post("/api/v1/hierarchy/seed/reset")
    def reset_seed() -> dict:
        hierarchy_store.replace_all(quote_seed_nodes())
        return {"ok": True, "count": hierarchy_store.size()}

    @app.get("/api/v1/cases")
    def list_cases(
        featureKey: Optional[str] = None,
        feature: Optional[str] = None,
        scene: Optional[str] = None,
        moduleName: Optional[str] = None,
        module_name: Optional[str] = None,
    ) -> dict:
        return case_query.query(
            featureKey,
            feature,
            scene,
            pick_label(moduleName, module_name, "模块") or None,
        )

    @app.get("/api/v1/tech-mappings/flow-nodes")
    def tech_flow_nodes() -> dict:
        resolved_nodes = api_resolve.list_flow_nodes()
        return {"source": "configs/quote.json", "total": len(resolved_nodes), "flowNodes": resolved_nodes}

    @app.get("/api/v1/tech-mappings/resolve")
    def tech_resolve(
        featureKey: Optional[str] = None,
        feature: Optional[str] = None,
        scene: Optional[str] = None,
    ) -> dict:
        return api_resolve.resolve(featureKey, feature, scene)

    @app.get("/api/v1/feature-maps/{feature_key:path}")
    def get_feature_map(feature_key: str) -> dict:
        key = from_path_variable(feature_key)
        result = map_query.find_by_feature_key(key)
        if result is None:
            raise NotFoundError("功能点不存在：" + key)
        return result

    @app.post("/api/v1/produce/batches", status_code=201)
    def create_batch(body: CreateBatchBody) -> dict:
        return produce.create(body.model_dump())

    @app.get("/api/v1/produce/batches/{batch_id}")
    def get_batch(batch_id: str) -> dict:
        batch = produce.find(batch_id)
        if batch is None:
            raise NotFoundError("导入批次不存在：" + batch_id)
        return batch

    @app.post("/api/v1/produce/batches/{batch_id}/cases")
    async def import_cases(batch_id: str, file: UploadFile = File(...)) -> dict:
        if produce.find(batch_id) is None:
            raise NotFoundError("导入批次不存在：" + batch_id)
        file_name = file.filename or ""
        if not file_name.strip():
            raise ValueError("文件名不能为空")
        content = await file.read()
        return produce.import_cases(batch_id, file_name, content)

    @app.post("/api/v1/produce/batches/{batch_id}/knowledge")
    async def import_knowledge(batch_id: str, file: UploadFile = File(...)) -> dict:
        if produce.find(batch_id) is None:
            raise NotFoundError("导入批次不存在：" + batch_id)
        file_name = file.filename or ""
        if not file_name.strip():
            raise ValueError("文件名不能为空")
        content = await file.read()
        return produce.import_knowledge(batch_id, file_name, content)

    @app.post("/api/v1/produce/batches/{batch_id}/knowledge/extract")
    def extract(batch_id: str) -> dict:
        if produce.find(batch_id) is None:
            raise NotFoundError("导入批次不存在：" + batch_id)
        return produce.extract_knowledge(batch_id)

    @app.post("/api/v1/produce/batches/{batch_id}/knowledge/text")
    def knowledge_text(batch_id: str, body: KnowledgeTextBody) -> dict:
        if produce.find(batch_id) is None:
            raise NotFoundError("导入批次不存在：" + batch_id)
        return produce.import_knowledge_text(batch_id, body.sourceName, body.text)

    @app.post("/api/v1/produce/batches/{batch_id}/keywords/confirm")
    async def confirm_keywords(batch_id: str, request: Request) -> dict:
        if produce.find(batch_id) is None:
            raise NotFoundError("导入批次不存在：" + batch_id)
        payload = await _optional_json(request)
        if payload:
            payload = ConfirmKeywordsBody(**payload).model_dump()
        return produce.confirm_keywords(batch_id, payload)

    @app.post("/api/v1/produce/batches/{batch_id}/draft")
    def draft(batch_id: str) -> dict:
        if produce.find(batch_id) is None:
            raise NotFoundError("导入批次不存在：" + batch_id)
        return produce.generate_draft(batch_id)

    @app.get("/api/v1/reviews")
    def reviews(
        batchId: Optional[str] = None,
        system: Optional[str] = None,
        status: Optional[str] = None,
        kind: Optional[str] = None,
        pool: Optional[str] = None,
        keyword: Optional[str] = None,
    ) -> dict:
        return produce.query_reviews(batchId, system, status, kind, pool, keyword)

    @app.patch("/api/v1/reviews/{review_id}")
    async def patch_review(review_id: str, request: Request) -> dict:
        payload = await _optional_json(request) or {}
        return produce.update_review(review_id, payload)

    @app.post("/api/v1/reviews/{review_id}/confirm")
    async def confirm_review(review_id: str, request: Request) -> dict:
        return produce.confirm_review(review_id, await _optional_json(request))

    @app.post("/api/v1/reviews/{review_id}/discard")
    async def discard_review(review_id: str, request: Request) -> dict:
        return produce.discard_review(review_id, await _optional_json(request))

    @app.post("/api/v1/reviews/batch/confirm")
    def confirm_batch(body: BatchConfirmBody) -> dict:
        return produce.confirm_batch(body.model_dump())

    @app.post("/api/v1/reviews/batches/{batch_id}/publish")
    async def publish(batch_id: str, request: Request) -> dict:
        return produce.publish_batch(batch_id, await _optional_json(request))

    if static_dir.exists():
        app.mount("/", StaticFiles(directory=static_dir, html=True), name="static")
    return app


app = build_app()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="127.0.0.1", port=8787, reload=False)
