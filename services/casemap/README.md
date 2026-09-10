# 层级 / 功能点地图服务（Python / FastAPI）

Java 版 `services/hierarchy` 的对等实现，接口路径与返回结构保持一致，默认端口 **8787**。需要 Python 3.9+。

## 启动

```bash
cd services/casemap
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8787
```

- 管理页：http://127.0.0.1:8787/
- 健康检查：http://127.0.0.1:8787/health
- 级联：http://127.0.0.1:8787/api/v1/hierarchy/cascade?domain=家装
- 总览树：http://127.0.0.1:8787/api/v1/overview/tree
- 资产生产：http://127.0.0.1:8787/produce.html
- 功能点地图：http://127.0.0.1:8787/feature-map.html

## 测试

```bash
cd services/casemap
python3 -m pytest
```

## API

| 方法 | 路径 |
|---|---|
| GET | `/health` |
| GET | `/api/v1/hierarchy/tree` |
| GET | `/api/v1/hierarchy/cascade` |
| GET | `/api/v1/overview/tree` |
| GET | `/api/v1/overview/cascade` |
| GET | `/api/v1/hierarchy/nodes` |
| GET | `/api/v1/hierarchy/features/{featureKey}` |
| POST | `/api/v1/hierarchy/nodes` |
| PATCH | `/api/v1/hierarchy/nodes/{id}` |
| POST | `/api/v1/hierarchy/nodes/{id}/disable` |
| GET | `/api/v1/feature-keys/examples` |
| POST | `/api/v1/feature-keys/encode` |
| POST | `/api/v1/feature-keys/decode` |
| GET | `/api/v1/cases?feature=&scene=` |
| GET | `/api/v1/cases?featureKey=` |
| GET | `/api/v1/cases?featureKey=&moduleName=`（可选模块筛选） |
| GET | `/api/v1/feature-maps/{featureKey}` |
| GET | `/api/v1/tech-mappings/flow-nodes` |
| GET | `/api/v1/tech-mappings/resolve?feature=&scene=` |
| POST | `/api/v1/produce/batches` |
| GET | `/api/v1/produce/batches/{batchId}` |
| POST | `/api/v1/produce/batches/{batchId}/cases` |
| POST | `/api/v1/produce/batches/{batchId}/knowledge` |
| POST | `/api/v1/produce/batches/{batchId}/knowledge/extract` |
| POST | `/api/v1/produce/batches/{batchId}/knowledge/text` |
| POST | `/api/v1/produce/batches/{batchId}/keywords/confirm` |
| POST | `/api/v1/produce/batches/{batchId}/draft` |
| GET | `/api/v1/reviews` |
| PATCH | `/api/v1/reviews/{reviewId}` |
| POST | `/api/v1/reviews/{reviewId}/confirm` |
| POST | `/api/v1/reviews/{reviewId}/discard` |
| POST | `/api/v1/reviews/batch/confirm` |
| POST | `/api/v1/reviews/batches/{batchId}/publish` |

`featureKey` 路径按段编码、保留斜杠，例如 `/api/v1/feature-maps/家装/报价/金额计算与汇总/数量价汇总`。

创建批次可传 `domain` / `system` / `moduleName`，或平台项目字段 `ziroom_domain` / `system_ref` / `module_name`。写入后两套名字都会出现在批次上，清洗仍挂到场景 / 功能点 / 流程节点，**不会**改成测试点。`system_ref` 必须是系统名称，不能含 `/`。不传 `moduleName` 时查询行为与原来一致。

总览树 `GET /api/v1/overview/tree` 按 **领域 → 系统 → 模块** 分组，模块下再列场景、功能点、用例。没有 `moduleName` 的正式资产归入「未填写模块」。功能点目录仍走 `/api/v1/hierarchy/tree`（领域 → 系统 → 场景 → 功能点）。

报价样例对拍夹具在 `tests/fixtures/quote_replay/`：带场景/功能点的 8 条应自动挂载，无标签的 3 条靠知识命中功能点但仍需人工确认场景。平台三个字段入参不得改变这组挂载结果。

不存在于层级、且没有正式用例、也没有直接 API 映射命中的功能点返回 **404**。层级缺失但有正式用例或 API 映射时返回 `meta.synthetic=true` 的合成地图。

## 运行时数据

种子数据在 `data/`。运行时文件写入 `runtime/`（已 gitignore）：

- `runtime/hierarchy_store.json`
- `runtime/produce/import_batches.json`
- `runtime/produce/review_queue.json`
- `runtime/produce/case_assets.json`

Java 版仍保留在 `services/hierarchy/`，作为行为对照。
