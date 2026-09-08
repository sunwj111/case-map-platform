# 层级 / 功能点地图服务（Java / Spring Boot）

领域 / 系统 / 场景 / 功能点与资产生产正式后端，对应 **M1-S01 ~ M1-S05、M1-S08、M2-S07、M3-S01 ~ M3-S03、B-01 ~ B-06、B-08，以及 C-01 ~ C-04、C-07**。

> 技术栈：Java 17 · Spring Boot 3.3 · 本地 JSON 存储（可后续换 MySQL + MyBatis-Plus）

## 启动

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home
cd services/hierarchy
./mvnw spring-boot:run
```

- 管理页：http://127.0.0.1:8787/
- 健康检查：http://127.0.0.1:8787/health
- 级联：http://127.0.0.1:8787/api/v1/hierarchy/cascade?domain=家装

## 测试

```bash
./mvnw test
```

## API（与此前约定一致）

| 方法 | 路径 |
|---|---|
| GET | `/api/v1/hierarchy/tree` |
| GET | `/api/v1/hierarchy/cascade` |
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

## 资产生产标准模式（B-01）

- 创建批次时校验领域和系统，只开放 `mode=standard`。
- 标准模式固定要求历史用例和知识库两项输入。
- 批次状态持久化到 `data/produce/import_batches.json`。
- 双源导入完成后才能校准关键字，确认关键字后才能生成地图草稿。
- 历史用例支持 CSV、XLSX、XLS、XMind，包含模糊表头映射、导入风险和原始用例 ID 追溯。
- 知识库支持 TXT、MD、Markdown、XLSX、XLS、XMind；Excel/XMind 转成可继续抽取的 Markdown 草稿。
- XMind 同时支持新版 `content.json` 和旧版 `content.xml`，限制解压内容、主题数与层级，并禁用 XML 外部实体。
- 自然语言和结构化 Markdown 可抽取场景、功能点、规则、流程节点候选，保留来源、置信度和来源版本。
- 支持直接粘贴知识文本，也支持对已上传知识文件重新抽取。
- 粘贴知识会与已上传文件合并，不再覆盖原始知识库。
- Markdown 表格、接口路径和 Java 调用链可提取为功能点与流程节点候选。
- 关键字确认后按历史字段和知识命中计算置信度，生成自动挂载、待抽检、人工确认三类用例。
- 知识库中未被历史用例覆盖的功能点生成缺口候选。
- 地图草稿自动生成持久化评审队列，支持批次、状态、类型和关键词筛选。
- 支持单条调整、确认、废弃和操作留痕；知识缺口必须补齐步骤与预期后才能确认。
- 支持批量确认并逐条返回失败原因。
- 已确认用例可发布到持久化正式资产库，并立即通过 `/api/v1/cases` 与 `/api/v1/feature-maps/{featureKey}` 查询。
- 正式资产保留原始用例 ID、知识来源版本、评审来源类型、批次和评审项 ID。
- 文件元数据记录格式、大小、SHA-256、行数和工作表信息。
- 文件重新导入后自动清除旧的关键字确认和地图草稿状态。
- OpenAPI：[`openapi/produce-import.yaml`](openapi/produce-import.yaml)。
- 正式功能验证页：http://127.0.0.1:8787/produce.html
- 功能点地图查询验证页：http://127.0.0.1:8787/feature-map.html

## 正式资产与技术映射（M2-S07 / M3-S01 ~ M3-S03）

| Story | 能力 |
|---|---|
| M2-S07 | 只返回 `已确认` 且非 `已归档` 的用例；可按 feature / scene / featureKey 过滤 |
| M3-S01 | 加载 `feature_api_map.json`，按功能点名命中，主接口优先 |
| M3-S02 | 加载 `quote.json` 的 flowNodes，与接口映射节点合并去重 |
| M3-S03 | `resolve` 返回 apis / flowNodes / service / source；无映射给回退接口 |

## FeatureMapDTO（M1-S02 / M1-S03）

| 产物 | 路径 |
|---|---|
| Java DTO | `com.casemap.hierarchy.featuremap.FeatureMapDto` |
| 组装器 | `FeatureMapAssembler`（M1-S04 / M1-S05 / E-04 / E-06 / E-07） |
| 查询服务 | `FeatureMapQueryService` + `GET /api/v1/feature-maps/{featureKey}`（M1-S08 / E-08 / E-09） |
| 质量摘要 | `QualitySummaryService`（I-01 地图侧口径） |
| 规则风险 | `RuleRiskService` + `QuoteKnowledgeCatalog`（I-02 地图侧） |
| MapNode 投影 | `FeatureMapNodeProjector` |
| 规范 + DOM 对照 | [`docs/feature_map_dto_spec.md`](../../docs/feature_map_dto_spec.md) |
| OpenAPI | [`openapi/feature-map.yaml`](openapi/feature-map.yaml) |
| TypeScript | [`contracts/feature-map.ts`](contracts/feature-map.ts) |

下一故事：**E-10** 来源、置信度和 stale 状态；或 **F** 功能点详情三视图消费已查询的 FeatureMapDTO。

## 说明

- featureKey 规范：[`docs/feature_key_spec.md`](../../docs/feature_key_spec.md)
- 早期 Python 试验版已移至 `services/hierarchy-python/`（仅作参考，不再作为正式交付）
