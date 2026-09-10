# 用例地图平台 · 用例地图 v1候选版

基于《用例地图平台建设方案-2.pptx》与能力要求生成。

## 正式服务（进行中）

| 服务 | 说明 |
|---|---|
| [`services/casemap`](services/casemap/README.md) | **Python / FastAPI**：层级 + FeatureMapDTO + 资产生产 |
| [`services/hierarchy`](services/hierarchy/README.md) | Java / Spring Boot 对照实现（接口一致） |
| [`docs/feature_key_spec.md`](docs/feature_key_spec.md) | featureKey 规范（M1-S01） |
| [`docs/feature_map_dto_spec.md`](docs/feature_map_dto_spec.md) | FeatureMapDTO / MapNode（M1-S02 / M1-S03） |

```bash
cd services/casemap
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8787
```

Java 对照启动：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home
cd services/hierarchy
./mvnw spring-boot:run
```

> 早期试验目录 `services/hierarchy-python/` 已废弃，正式 Python 服务在 `services/casemap/`。

## 交付物

| 文件 | 说明 |
|---|---|
| `output/用例地图平台_用例地图_v1候选版.xlsx` | 主交付：测试地图/挂载/覆盖矩阵/缺口/回归/查询与生命周期 |
| `Downloads/用例地图平台_用例地图_v1候选版.xlsx` | 同上副本，便于直接打开 |
| `knowledge/case_map_platform_kb.md` | 开发知识库骨架 |
| `configs/case_map_platform.json` | 领域配置（16节点/27状态/32字段/18规则） |
| `input/case_map_platform_seed_cases.xlsx` | 方案衍生种子用例（51条） |

## 工作表

00~09 为标准用例地图；10~13 为平台能力增强：

- `10_多维查询与追溯索引`：关键词/场景/应用/接口/上下游/多层级追溯
- `11_场景管理清单`
- `12_版本与生命周期`
- `13_用例层级视图规范`：领域→节点→依据→用例

## 重新生成

```bash
python3 scripts/generate_case_map.py \
  --cases input/case_map_platform_seed_cases.xlsx \
  --knowledge knowledge/case_map_platform_kb.md \
  --config configs/case_map_platform.json \
  --domain case_map_platform \
  --output output/用例地图平台_用例地图_v1候选版.xlsx
```

## 注意

本版为 **v1候选版**。当前无质保平台历史导出，种子用例由建设方案衍生；低置信度挂载、P0/P1缺口需人工评审后入库。
若后续提供真实历史用例 Excel，可替换 `input/` 后重新生成。
