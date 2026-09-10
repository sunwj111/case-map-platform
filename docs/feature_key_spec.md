# featureKey 规范（领域 / 系统 / 场景 / 功能点）

> 对应 Story：**M1-S01**  
> 实现：`services/hierarchy`（Java Spring Boot · `com.casemap.hierarchy.featurekey.FeatureKey`）

## 规则

```text
featureKey = {领域}/{系统}/{场景}/{功能点}
```

- 固定 **4 段**，分隔符为 `/`
- 段内 **禁止** 再出现 `/`、换行
- 段首尾空白会 trim
- 展示名可含中文、空格、`-`、`_` 等；若需上 URL，对**整段**做百分号编码

## URL

| 场景 | 做法 |
|---|---|
| Path | `/api/v1/hierarchy/features/{domain}/{system}/{scene}/{feature}`，每段单独编码，斜杠保留 |
| Path | `/api/v1/feature-maps/{domain}/{system}/{scene}/{feature}`，规则同上 |
| Query | `?featureKey={urlencoded_featureKey}` |

不要把整段 featureKey 的 `/` 编码成 `%2F` 再塞进单个 path segment，Tomcat 会按非法路径拒绝。

编解码方法（Java）：

- `FeatureKey.encodeForUrl`
- `FeatureKey.decodeFromUrl`

## 报价域示例（验收）

1. `家装/报价/金额计算与汇总/数量价汇总`
2. `家装/报价/金额计算与汇总/固定价汇总`
3. `家装/报价/造价提交与审核/自动审核判定`

## 与原型差异

| 原型总览级联 | 正式 featureKey |
|---|---|
| 领域 → 应用 → **模块** → 功能点 → 场景 | 领域 → **系统** → **场景** → 功能点 |

正式协议以本文件为准；原型里的「模块」不进入 featureKey。

## 模块（moduleName）

批次与正式用例可带可选字段 `moduleName`（对应平台 `Project.module_name`），用于总览分组和按模块筛选。

- **不是** featureKey 的第四段；第四段仍是功能点
- 不参与清洗匹配
- 段内禁止 `/` 与换行；缺省为空字符串
- 平台字段别名：`ziroom_domain` → 领域，`system_ref` → 系统，`module_name` → 模块
- 创建批次可只传平台字段；与 `domain` / `system` / `moduleName` 同时传时必须一致
- 批次不要求领域/系统已在功能点目录中；`system_ref` 必须是系统名，不能是带 `/` 的 URL
- 报价对拍：`tests/fixtures/quote_replay/`，带标签用例自动挂载数与功能点不得回退
- `GET /api/v1/cases` 增加可选 `moduleName`（或 `module_name`）；不传时行为与原来一致
- 总览树 `GET /api/v1/overview/tree`：领域 → 系统 → 模块；模块下再挂场景 / 功能点 / 用例
- 空模块展示为「未填写模块」，查询过滤值仍是空字符串
- 功能点目录 `/api/v1/hierarchy/tree` 仍是领域 → 系统 → 场景 → 功能点，不要把模块插入 `featureKey`
