# 家政履约系统 — 冻结假设与状态流转

> 来源：需求分析计划第 8 节。用户批准计划后按推荐假设冻结，后续设计与实现以此为准。  
> 状态：已冻结（2026-08-11）

## 1. 冻结假设

| 编号 | 主题 | 冻结结论 |
|------|------|----------|
| A1 | 剩余次数口径 | `剩余 = 总次数 - 已用次数 - 有效占用次数`。派单成功增加有效占用；结算完成将占用转为已用；取消/拒绝/失效/履约失败释放占用。 |
| A2 | 同一计划服务时段 | 以计划开始时间与计划结束时间的**闭开区间重叠**判定冲突（`startA < endB && startB < endA`）。若未显式传结束时间，则 `计划结束 = 计划开始 + 服务项目标准时长`。非“时刻完全相等”才算冲突。 |
| A3 | 客户可取消时机 | **草稿、待派单、待服务**可取消；取消后释放已形成的套餐占用与档期占用（若有）。**服务中及之后**客户不可直接取消，仅走运营履约失败或异常路径。 |
| A4 | 拒绝 vs 失效 | **拒绝**：运营主动拒单（典型于待派单）。**失效**：超时或运营置失效（待派单/待服务）。两者均释放有效占用并保留历史，状态分别为 `REJECTED` / `EXPIRED`。 |
| A5 | 正常扣次规则 | 正常履约基础扣 **1 次**。异常调整的扣次与金额由后端按异常类型规则计算；前端只展示预览与结果，不允许把前端提交值当作最终扣减/金额。 |
| A6 | 异常类型 | 枚举至少：`LATE`（迟到）、`SHORT_DURATION`（时长不足）、`MISSING_ITEM`（项目缺失）、`EXTRA_SERVICE`（额外服务）、`CUSTOMER_CANCEL`（客户临时取消）、`OTHER`（其他）。 |
| A7 | 确认与异常互斥 | 处于 `PENDING_CONFIRM`：客户要么**确认**进入结算完成路径，要么**发起异常**进入 `EXCEPTION_PROCESSING`；不可同时存在“已确认且仍有未处理异常”。 |
| A8 | 完成幂等 | `complete-settlement` 二次调用：若已是 `COMPLETED`，返回业务错误（或明确幂等成功响应），且**不得二次扣减**套餐、不得重复写结算结果。 |
| A9 | 权限模拟 | 使用 Header / `LOGIN_ACCESS_TOKEN` 解析当前用户；运营与客户角色分离。客户仅能操作本人套餐与本人申请；运营可跨客户处理。 |
| A10 | 并发派单 | 派单在 DB 事务内重校验余额、技能、档期重叠；可选 Redis 分布式锁降低同人同时段并发冲突；**最终以 DB 校验为准**，不信任可用性缓存。 |
| A11 | 中间件 | 采用 **方案 A：Redis 服务人员可用性查询缓存**。TTL 建议 3 分钟；不可用时降级查库；派单/占用变更后失效相关缓存。不做方案 B（MQ）。 |
| A12 | 占用生命周期 | 占用记录不物理删除。状态：`VALID` → `RELEASED`（取消/拒绝/失效/失败）或 `CONVERTED`（档期在服务开始或履约确认后；套餐次数在结算完成时）。 |

## 2. 异常类型默认调整规则（后端权威）

| 异常类型 | 默认扣次调整 | 默认费用调整方向 | 说明 |
|----------|--------------|------------------|------|
| LATE | 仍扣 1 次 | 可产生补偿/减免金额（运营确认） | 服务仍完成，次数正常扣，金额可调 |
| SHORT_DURATION | 仍扣 1 次或 0 次（运营结论） | 可减免 | 以后端处理结果为准 |
| MISSING_ITEM | 默认扣 1 次 | 可减免 | 缺项不影响“是否履约过”，调整走金额/意见 |
| EXTRA_SERVICE | 默认扣 1 次 | 可增收 | 额外服务不计额外套餐次数（轻量规则） |
| CUSTOMER_CANCEL | 默认扣 0 次 | 可计取消费用 | 释放或转扣由处理结果决定 |
| OTHER | 由运营填写调整结果 | 由运营确认 | 必须有处理意见 |

> 上表为可落地的默认策略；运营处理异常时可在后端规则允许范围内确认最终 `adjustedDeductTimes` 与 `adjustedAmount`，但**禁止前端绕过后端直接落库最终值**。

## 3. 冻结状态机

### 3.1 状态枚举

`DRAFT`、`PENDING_DISPATCH`、`PENDING_SERVICE`、`IN_SERVICE`、`PENDING_CONFIRM`、`EXCEPTION_PROCESSING`、`COMPLETED`、`CANCELED`、`REJECTED`、`EXPIRED`、`FAILED`

### 3.2 合法流转（业务动作 → 目标状态）

| 动作 | 角色 | 允许起始状态 | 目标状态 | 占用影响 |
|------|------|--------------|----------|----------|
| saveDraft | 客户 | （新建）/ DRAFT | DRAFT | 无 |
| submit | 客户 | DRAFT 或新建直接提交 | PENDING_DISPATCH | 无 |
| cancel | 客户 | DRAFT, PENDING_DISPATCH, PENDING_SERVICE | CANCELED | 有占用则释放 |
| reject | 运营 | PENDING_DISPATCH | REJECTED | 无占用或释放（通常尚未占用） |
| expire | 运营 | PENDING_DISPATCH, PENDING_SERVICE | EXPIRED | 有占用则释放 |
| dispatch | 运营 | PENDING_DISPATCH | PENDING_SERVICE | 同时形成套餐+档期 VALID 占用 |
| startService | 运营 | PENDING_SERVICE | IN_SERVICE | 档期占用可转为 CONVERTED |
| recordFulfillment | 运营 | IN_SERVICE | PENDING_CONFIRM | 写入履约记录；时长后端计算 |
| fail | 运营 | PENDING_SERVICE, IN_SERVICE | FAILED | 释放有效占用 |
| customerConfirm | 客户 | PENDING_CONFIRM | PENDING_CONFIRM（标记已确认） | 不改占用；随后由运营结算 |
| raiseException | 客户 | PENDING_CONFIRM | EXCEPTION_PROCESSING | 创建未处理异常 |
| handleException | 运营 | EXCEPTION_PROCESSING | EXCEPTION_PROCESSING（异常已处理） | 记录调整；结算前再生效 |
| completeSettlement | 运营 | PENDING_CONFIRM（已确认且无未处理异常）或 EXCEPTION_PROCESSING（异常均已处理） | COMPLETED | 套餐占用 VALID→CONVERTED，已用+N |

### 3.3 非法流转原则

- 禁止通用状态 PATCH/编辑接口。
- 终态（`COMPLETED`/`CANCELED`/`REJECTED`/`EXPIRED`/`FAILED`）不可再迁出。
- 存在未处理异常时禁止 `completeSettlement`。
- 履约记录不完整或客户未确认（无异常路径）时禁止完成。

## 4. 角色权限冻结

| 能力 | 客户 | 运营 |
|------|------|------|
| 草稿/提交/本人取消 | 是 | 否（不代客提交，除非后续另开需求） |
| 查本人申请/套餐 | 是 | 是（全量） |
| 派单/开始/履约录入/拒绝/失效/失败 | 否 | 是 |
| 客户确认/发起异常 | 是 | 否 |
| 处理异常/完成结算 | 否 | 是 |
| 可用人员查询 | 创建时可查 | 派单时可查 |

## 5. 变更控制

若需改动本文件任一冻结项，须先更新本文档并同步 `02-schema-and-api-design.md`，再改代码。
