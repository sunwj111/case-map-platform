# 层级 / 功能点地图服务（Java / Spring Boot）

领域 / 系统 / 场景 / 功能点 正式后端，对应 **M1-S01 ~ M1-S05、M2-S07、M3-S01 ~ M3-S03**。

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
| GET | `/api/v1/tech-mappings/flow-nodes` |
| GET | `/api/v1/tech-mappings/resolve?feature=&scene=` |

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
| 组装器 | `FeatureMapAssembler`（M1-S04 / M1-S05） |
| MapNode 投影 | `FeatureMapNodeProjector` |
| 规范 + DOM 对照 | [`docs/feature_map_dto_spec.md`](../../docs/feature_map_dto_spec.md) |
| OpenAPI | [`openapi/feature-map.yaml`](openapi/feature-map.yaml) |
| TypeScript | [`contracts/feature-map.ts`](contracts/feature-map.ts) |

下一故事：**M1-S08** `GET /feature-maps/{featureKey}`（对外查询；可选再补 M1-S06 summary 细化）。

## 说明

- featureKey 规范：[`docs/feature_key_spec.md`](../../docs/feature_key_spec.md)
- 早期 Python 试验版已移至 `services/hierarchy-python/`（仅作参考，不再作为正式交付）
