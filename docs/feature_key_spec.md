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
| Path | `/api/v1/hierarchy/features/{urlencoded_featureKey}` |
| Query | `?featureKey={urlencoded_featureKey}` |

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
