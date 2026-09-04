# 家装-报价 · 原料与最小交付表

## 放哪里

| 你提供的东西 | 放到 |
|---|---|
| 领域配置（节点/规则/状态机） | `configs/quote.json`（已有，改这里） |
| 开发知识库 Markdown | `input/quote/`（已有合并知识库、增量知识） |
| 核心接口清单 | `input/quote/家装报价生成及审核场景核心接口清单.md`（已有） |
| 功能点 ↔ 接口映射 | 同上文档第五节；机器副本 `prototype/data/feature_api_map.js` |
| **历史用例 Excel（待你放）** | `input/quote/历史用例.xlsx` |
| 人工确认后的四张表 | 覆盖 `output/quote_min_delivery/` 或另存 `input/quote/confirmed/` |

重新生成（本机执行）：

```bash
python3 scripts/generate_quote_min_delivery.py
```

## 四张最小交付表（已从现有材料生成）

目录：`output/quote_min_delivery/`

| 文件 | 来源 | 还要你做什么 |
|---|---|---|
| `01_节点上下游.csv` | quote.json 节点顺序 + 全景边 + 状态机 | QA 确认主流程/分支是否正确 |
| `02_功能点_接口.csv` | 接口清单第五节 / feature_api_map | 研发确认「主接口」 |
| `03_用例_功能点_接口.csv` | 功能点地图样例 8 条 | **换成历史用例导出**；无接口列则继承功能点主接口 |
| `04_接口下游.csv` | 接口清单「下游依赖」 | 研发确认强弱、同步异步 |

Excel 直接打开即可。`确认状态=待确认` 的行需要人工勾成「已确认」后才能当正式图谱边。
