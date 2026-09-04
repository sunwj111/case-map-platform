# 用例地图平台 · 开发知识库分析（v1）

> 来源：
> 1. `/Users/sunwenjing/Downloads/用例地图平台建设方案-2.pptx`（2026-07，建设方案）
> 2. 用户补充能力要求：全量用例图谱、多维查询、多层级关联追溯、场景管理、用例版本、生命周期、层级查看
>
> 说明：本知识库用于搭建「用例地图平台」自身的测试地图骨架。当前无独立历史用例 Excel，种子用例由建设方案 MVP 与能力要求衍生，需人工评审后入库。

## 1. 产品定位

用例地图平台不是再建一个平铺用例列表，而是把开发知识库中的真实业务逻辑与历史测试资产连接起来，形成可检索、可评审、可复用、可演进的测试知识库。

核心价值：
1. 看清核心流程（领域 → 流程节点 → 上下游）
2. 盘活历史用例（重挂载到节点/状态/规则）
3. 说清覆盖缺口（状态、字段、规则矩阵）
4. 沉淀核心回归资产（按节点聚合）
5. 支撑质量运营（需求推荐、版本回归、自动化候选）

## 2. 分层架构

1. 输入层：历史用例 Excel 导入、开发知识库（txt/md）接入
2. 分析层：流程节点识别、状态/字段/规则覆盖分析、置信度计算、缺口识别
3. 评审层：自动挂载 + 人工抽检/确认/调整/废弃，批量评审
4. 资产层：用例地图、覆盖矩阵、缺口池、核心回归集、正式知识库
5. 应用层：新需求推荐、版本回归圈选、自动化优先级评估

## 3. 核心数据模型（四层）

领域 Domain
→ 流程节点 FlowNode
→ 测试依据 Evidence（状态流转 / 字段约束 / 业务规则 / 场景 / 应用 / 接口 / 上下游链路）
→ 用例资产 CaseAsset（历史用例 / 缺口用例 / 核心回归用例 / 版本化正式用例）

关键属性建议：
- domainCode、displayName、riskLevel
- flowNode、nodeDescription、upstreamNodes、downstreamNodes
- sceneCode、appCode、apiCode、linkPath
- caseId、caseVersion、lifecycleStatus、hierarchyLevel
- confidence、reviewStatus、sourceType、isCoreRegression、automationStatus
- knowledgeSourceVersion、originalCaseId

## 4. 核心业务流程节点

1. 领域配置管理：维护领域、流程节点、关键词、风险等级；预留多领域扩展
2. 历史用例导入：Excel 解析、字段映射、批量入库草稿
3. 知识库接入：接入业务流程、状态机、字段、规则文本/Markdown
4. 用例地图生成：基于知识库搭骨架，历史用例自动重挂载，产出可视化地图
5. 覆盖矩阵分析：状态、字段、规则三维覆盖与缺口量化
6. 缺口用例池管理：AI/规则生成缺口用例，支持在线评审、编辑、批量入库
7. 核心回归集管理：按节点聚合高价值回归候选，支持确认与调整
8. 人工评审校准：确认/调整/废弃/批量处理，置信度分层处理
9. 用例资产入库：评审通过进入正式知识库，支持全文检索与复用
10. 场景管理：场景创建、编辑、归档，场景与节点/用例绑定
11. 多维检索与图谱查询：按关键词、场景、应用、接口、上下游链路查询用例
12. 多层级关联追溯：从指定节点向上/向下追溯多层级关联用例
13. 用例版本管理：版本创建、对比、回滚、生效版本切换
14. 用例生命周期管理：草稿→待评审→已确认→已发布→已废弃（及回退）
15. 用例层级查看：领域/节点/依据/用例树形展开与钻取
16. 价值应用赋能：需求测试推荐、版本回归包、自动化候选输出

## 5. 状态机

### 5.1 CaseLifecycle（用例生命周期）
- 草稿(DRAFT) → 待评审(PENDING_REVIEW)：提交评审
- 待评审 → 已确认(CONFIRMED)：评审通过
- 待评审 → 草稿：退回修改
- 待评审 → 已废弃(DEPRECATED)：评审废弃
- 已确认 → 已发布(PUBLISHED)：发布入库/生效
- 已发布 → 已废弃：下线废弃
- 已确认 → 待评审：变更后重新评审
- 已废弃 → 草稿：复活重建（受控）

### 5.2 CaseVersion（用例版本）
- 编辑中(EDITING) → 已冻结(FROZEN)：冻结版本
- 已冻结 → 已生效(ACTIVE)：设为当前生效版本
- 已生效 → 已归档(ARCHIVED)：被新版本替换后归档
- 已冻结 → 已作废(VOID)：作废未生效版本

### 5.3 ReviewTask（评审任务）
- 待处理(OPEN) → 处理中(IN_PROGRESS)：认领
- 处理中 → 已完成(DONE)：提交结论
- 处理中 → 已取消(CANCELLED)：取消任务
- 待处理 → 已取消：批量取消

### 5.4 Scene（场景）
- 草稿 → 启用(ENABLED)
- 启用 → 停用(DISABLED)
- 停用 → 启用
- 启用/停用 → 已归档(ARCHIVED)

### 5.5 MapGenerationJob（地图生成任务）
- 待执行 → 解析中 → 挂载中 → 待评审 → 已完成
- 任意非终态 → 失败
- 失败 → 待执行（重试）

## 6. 核心实体字段

### Domain
- domainCode：领域编码
- displayName：领域名称
- status：启用状态
- configVersion：配置版本

### FlowNode
- nodeCode / nodeName
- riskLevel
- description
- keywords
- upstreamNodeCodes / downstreamNodeCodes

### CaseAsset
- caseId / caseName
- flowNode
- testType / riskLevel
- sceneCodes / appCodes / apiCodes
- upstreamLinks / downstreamLinks
- confidence / reviewStatus / lifecycleStatus
- caseVersion / hierarchyLevel
- sourceType / isCoreRegression / automationStatus
- knowledgeSourceVersion / originalCaseId

### CoverageMatrixItem
- evidenceType（state/field/rule）
- evidenceKey
- coverStatus
- matchedCaseCount
- gapPriority

### QueryTraceRequest
- keyword / scene / app / api / linkPath
- rootNode
- depth（追溯层级）
- direction（upstream/downstream/both）

## 7. 关键业务规则

1. CMP-R001 置信度分层：>=80 自动挂载；60-79 人工抽检；<60 人工确认
2. CMP-R002 缺口用例与低置信度用例不得直接进入正式全集，必须进评审池
3. CMP-R003 每条正式用例至少绑定一种测试依据（状态/字段/规则/场景/接口）
4. CMP-R004 核心回归优先纳入 P0/P1、高风险节点、状态流转、规则校验、数据一致性、历史缺陷相关
5. CMP-R005 生效版本唯一：同一 caseId 同时仅允许一个 ACTIVE 版本
6. CMP-R006 生命周期约束：仅 CONFIRMED 可发布；仅 PUBLISHED 可被回归包直接引用为正式基线
7. CMP-R007 多层级追溯 depth 默认 2，最大不超过配置上限（建议 5），超限拦截
8. CMP-R008 场景停用后不可新建挂载，但历史挂载只读可查
9. CMP-R009 上下游链路变更后需触发关联用例重算或标记 stale
10. CMP-R010 入库必填 knowledgeSourceVersion 与 originalCaseId（缺口用例可用生成编号）
11. CMP-R011 Excel 导入字段缺失时可容忍，但需降低置信度并提示风险
12. CMP-R012 AI 生成结果一律标记 sourceType=AI_GAP，必须人工把关
13. CMP-R013 多维查询至少支持关键词、场景、应用、接口、上下游链路组合过滤
14. CMP-R014 层级查看按 领域→流程节点→测试依据→用例资产 四层展示，禁止仅平铺列表作为主视图
15. CMP-R015 一期不改造质保平台、不强依赖实时接口、不建设复杂多级审批

## 8. 一期 MVP 范围

必建：
- 领域配置管理（报价先行，预留扩展）
- 历史用例导入
- 知识库接入
- 用例地图生成与挂载
- 全流程人工评审
- 缺口用例池
- 核心回归集
- 用例资产入库与检索

边界（暂不纳入）：
- 不改造现有质保平台
- 弱化实时接口依赖（离线导入）
- 简化跨团队审批
- AI 生成必须人工把关

用户要求增强（纳入本地图骨架，二期可深化实现）：
- 全量用例图谱
- 关键词/场景/应用/接口/上下游多维查询
- 指定节点多层级关联追溯
- 场景管理、版本管理、生命周期、层级查看

## 9. 异常与边界

- 空 Excel / 缺关键列 / 编码错误
- 知识库文本无状态机或规则片段
- 节点关键词冲突导致挂载歧义
- 追溯环路（A→B→A）
- 版本并发编辑冲突
- 大批量评审超时与部分失败回滚
- 场景/应用/接口编码不存在
- depth=0 或超大 depth
- 已废弃用例仍被回归包引用

## 10. 验收关注点（映射方案成功标准）

1. 平台落地：完成领域全流程导入与模型生成
2. 资产归位：历史用例按节点归类（目标 100%，实际以置信度分层统计）
3. 共识达成：覆盖缺口经评审确认并形成清单
4. 回归闭环：核心回归集按节点可执行
5. 知识沉淀：评审用例全量入库可复用
6. 查询追溯：支持多维查询与多层级关联查看
