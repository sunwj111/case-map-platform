# FeatureMapDTO 规范（M1-S02 / M1-S03）

> 实现：`services/hierarchy` · `com.casemap.hierarchy.featuremap`  
> 样例来源：`prototype/data/feature_map_samples.js`

## 主结构

```text
FeatureMapDTO
├── meta            地图元信息
├── spine           业务脊柱（域 / 系统 / 场景 / 功能点）
├── businessView    业务资产链（场景 → 用例 → 脚本 / 数据 / 执行）
├── techView        技术映射（服务 / 接口 / 表 / 消息 / 模块）
├── riskView        规则 / 缺陷 / 风险标签
├── summary         质量摘要
└── consumers       下游消费方（可选）
```

`featureKey` 规则见 [`feature_key_spec.md`](feature_key_spec.md)。

## MapNode（M1-S03）

列组件与图谱统一消费：

| 字段 | 说明 |
|---|---|
| `id` | 稳定节点 ID，如 `case:TC-001` |
| `type` | `feature` / `neighbor` / `scene` / `case` / `api` / `rule` / `script` / `defect` / `data` / `execution` / `service` / `module` |
| `title` | 展示标题 |
| `status` | `ready` / `review` / `missing` / `deprecated` |
| `confidence` | 0–100，可空 |
| `payload` | 原始业务对象 |

投影工具：`FeatureMapNodeProjector.project(FeatureMapDto)`。

## 与原型 DOM 对照

| DTO 路径 | 原型 DOM / 渲染入口 |
|---|---|
| `meta.featureKey` | URL / `state.featureKey` |
| `meta.qualityOwner` | `#ownerInfo` |
| `meta.updatedAt` | `#updatedAt` |
| `meta.dataSources` | 技术映射来源补充 |
| `spine.domain` / `app` / `featureName` / `sceneName` | `#spineBox`、`#pageTitle`、面包屑 |
| `spine.valueTags` | `#spineBox` 价值标签 |
| `spine.neighborFeatures` | `#spineBox` 邻接列表 |
| `businessView.scenarios` | `#sceneCol`、`#sceneCountTag` |
| `businessView.cases` | `#caseCol`、`#caseCountTag` |
| `businessView.scripts` | `#scriptCol` |
| `businessView.dataTemplates` | `#dataCol` |
| `businessView.executions` | `#execCol` |
| `riskView.rules` | `#ruleBox` |
| `riskView.defects` | `#bugBox` |
| `riskView.tags` | `#riskTagBox` |
| `techView.source` | `#techSource` |
| `techView.apis` / `flowNodes` / `services` | `#techFlow`、`#techHint` |
| `summary.linkedCaseCount` | `#donutNum`、`#linkedCount` |
| `summary.priorityDistribution` | `#priorityLegend`、`#donut` |
| `summary.automationCoverage` | `#autoRate`、`#autoBar` |
| `summary.defectCount30d` | `#bugCount` |
| `summary.gapCount` | `#gapCount` |
| `consumers` | `#consumerBox` |

## 契约文件

| 文件 | 用途 |
|---|---|
| [`openapi/feature-map.yaml`](../services/hierarchy/openapi/feature-map.yaml) | OpenAPI 3.0 |
| [`contracts/feature-map.ts`](../services/hierarchy/contracts/feature-map.ts) | TypeScript 类型 |
| Java：`com.casemap.hierarchy.featuremap.FeatureMapDto` | 后端主 DTO |

## 后续

- **M1-S04** `FeatureMapAssembler`：按 `featureKey` 聚合多源数据输出本 DTO  
- **M1-S08** `GET /api/v1/feature-maps/{featureKey}`：对外查询
