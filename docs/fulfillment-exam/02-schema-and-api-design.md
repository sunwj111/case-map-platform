# 家政履约系统 — 表结构草案与业务动作接口清单

> 依据：[01-frozen-assumptions.md](./01-frozen-assumptions.md)  
> 范围：仅设计，不写业务代码  
> 技术底座：`a0-exam-backend-scaffold` + `vue3-admin-develop-template`

## 1. 表结构草案

### 1.1 ER 关系（逻辑）

- `hm_customer` 1—N `hm_service_package`
- `hm_service_package` N—N `hm_service_item`（经 `hm_package_service_item`）
- `hm_service_staff` N—N `hm_service_item`（经 `hm_staff_skill`）
- `hm_fulfillment_request` N—1 `hm_customer` / `hm_service_package` / `hm_service_item`
- `hm_fulfillment_request` 1—0..1 `hm_package_quota_occupancy`（当前有效占用可多版本历史）
- `hm_fulfillment_request` 1—0..1 `hm_staff_schedule_occupancy`（同上，历史多条）
- `hm_fulfillment_request` 1—0..1 `hm_fulfillment_record`
- `hm_fulfillment_request` 1—N `hm_exception_settlement`
- `hm_fulfillment_request` 1—N `hm_operation_log`

### 1.2 表定义

#### hm_customer（客户）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| external_user_id | VARCHAR(64) UK | 登录身份绑定 |
| customer_name | VARCHAR(64) | |
| mobile | VARCHAR(32) | 可空 |
| status | VARCHAR(16) | ACTIVE/DISABLED |
| created_at / updated_at | DATETIME | |

#### hm_service_item（服务项目）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| item_code | VARCHAR(32) UK | |
| item_name | VARCHAR(64) | |
| standard_duration_minutes | INT | 用于推算计划结束时间 |
| status | VARCHAR(16) | ACTIVE/DISABLED |

#### hm_service_package（服务套餐）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| customer_id | BIGINT FK | |
| package_code | VARCHAR(32) UK | |
| package_name | VARCHAR(64) | |
| total_times | INT | 总次数 |
| used_times | INT | 已用次数 |
| occupied_times | INT | 有效占用次数 |
| package_amount | DECIMAL(12,2) | 套餐金额 |
| valid_from / valid_to | DATETIME | 有效期 |
| status | VARCHAR(16) | ACTIVE/EXPIRED/DISABLED |
| version | INT | 乐观锁 |
| created_at / updated_at | DATETIME | |

**剩余次数**：`total_times - used_times - occupied_times`（应用层计算，可不落库）。

建议索引：`(customer_id, status)`、`(valid_to)`。

#### hm_package_service_item（套餐可预约项目）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| package_id | BIGINT FK | |
| service_item_id | BIGINT FK | |
| UK(package_id, service_item_id) | | |

#### hm_service_staff（服务人员）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| staff_code | VARCHAR(32) UK | |
| staff_name | VARCHAR(64) | |
| status | VARCHAR(16) | AVAILABLE/DISABLED |
| created_at / updated_at | DATETIME | |

#### hm_staff_skill（人员技能）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| staff_id | BIGINT FK | |
| service_item_id | BIGINT FK | |
| UK(staff_id, service_item_id) | | |

#### hm_fulfillment_request（履约申请）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| request_no | VARCHAR(32) UK | 业务单号 |
| customer_id | BIGINT FK | |
| package_id | BIGINT FK | |
| service_item_id | BIGINT FK | |
| planned_start_time | DATETIME | |
| planned_end_time | DATETIME | |
| service_address | VARCHAR(256) | 去空白后非空 |
| requirement_desc | VARCHAR(512) | 去空白后非空 |
| status | VARCHAR(32) | 状态枚举 |
| staff_id | BIGINT NULL | 派单后填写 |
| customer_confirmed | TINYINT | 0/1 |
| settlement_confirmed | TINYINT | 0/1 |
| final_deduct_times | INT NULL | 完成后落定 |
| final_adjust_amount | DECIMAL(12,2) NULL | 完成后落定 |
| version | INT | 乐观锁 |
| created_at / updated_at | DATETIME | |

建议索引：
- `(customer_id, status, created_at)`
- `(package_id, planned_start_time, planned_end_time, status)` — 支撑同时段唯一校验
- `(staff_id, status)`
- `(status, updated_at)` — 列表筛选与统计

#### hm_package_quota_occupancy（套餐次数占用历史）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| request_id | BIGINT FK | |
| package_id | BIGINT FK | |
| occupy_times | INT | 通常为 1 |
| status | VARCHAR(16) | VALID/RELEASED/CONVERTED |
| occupied_at / released_at / converted_at | DATETIME | |
| remark | VARCHAR(256) | |

建议索引：`(package_id, status)`、`UK` 可选：同一 request 仅一条非 RELEASED 的有效链路用应用保证。

#### hm_staff_schedule_occupancy（人员档期占用历史）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| request_id | BIGINT FK | |
| staff_id | BIGINT FK | |
| start_time / end_time | DATETIME | |
| status | VARCHAR(16) | VALID/RELEASED/CONVERTED |
| occupied_at / released_at / converted_at | DATETIME | |

建议索引：`(staff_id, status, start_time, end_time)` — 重叠查询。

#### hm_fulfillment_record（履约记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| request_id | BIGINT UK | 一单一条 |
| actual_start_time / actual_end_time | DATETIME | |
| completed_item_ids | VARCHAR(256) | 或子表；MVP 可用 JSON/逗号 |
| result_code | VARCHAR(32) | SUCCESS/PARTIAL/FAILED |
| actual_duration_minutes | INT | **后端计算** |
| remark | VARCHAR(512) | |
| created_at / updated_at | DATETIME | |

#### hm_exception_settlement（异常与结算处理）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| request_id | BIGINT FK | |
| exception_type | VARCHAR(32) | A6 枚举 |
| exception_desc | VARCHAR(512) | 客户反馈 |
| handle_status | VARCHAR(16) | OPEN/HANDLED |
| handler_opinion | VARCHAR(512) | |
| responsibility | VARCHAR(32) | STAFF/CUSTOMER/COMPANY/SHARED |
| adjusted_deduct_times | INT NULL | 后端确认后写入 |
| adjusted_amount | DECIMAL(12,2) NULL | 后端确认后写入 |
| raised_by / handled_by | VARCHAR(64) | |
| raised_at / handled_at | DATETIME | |

建议索引：`(request_id, handle_status)`。

#### hm_operation_log（处理记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| request_id | BIGINT FK | |
| action | VARCHAR(64) | submit/dispatch/... |
| from_status / to_status | VARCHAR(32) | |
| operator_id | VARCHAR(64) | |
| operator_role | VARCHAR(16) | CUSTOMER/OPERATOR |
| detail_json | TEXT | |
| created_at | DATETIME | |

索引：`(request_id, created_at)`。

---

## 2. 业务动作接口清单

统一前缀：`/api/fulfillment-requests`  
统一响应：`{ code, message, data }`（沿用脚手架约定）  
鉴权：请求头携带登录 Token；后端解析用户与角色。

### 2.1 查询类

| 方法 | 路径 | 角色 | 说明 |
|------|------|------|------|
| GET | `/api/fulfillment-requests` | 客户/运营 | 分页列表；客户强制本人过滤；支持 status、时间、套餐等筛选 |
| GET | `/api/fulfillment-requests/stats` | 客户/运营 | 轻量统计：待派单/待服务/异常处理中/已完成等 |
| GET | `/api/fulfillment-requests/{id}` | 客户/运营 | 详情：申请 + 套餐摘要 + 占用 + 履约 + 异常 + 操作历史 |
| GET | `/api/fulfillment-requests/available-staff` | 客户/运营 | query：serviceItemId、plannedStart、plannedEnd；Redis 缓存 + DB 降级 |
| GET | `/api/fulfillment-requests/{id}/settlement-preview` | 运营 | 后端计算计划/实际/扣次/调整/结算方向预览 |
| GET | `/api/my-packages` | 客户 | 本人有效套餐及剩余次数、可预约项目（辅助预约表单） |

### 2.2 客户动作

| 方法 | 路径 | 起始状态 | 成功后状态 | 关键校验 |
|------|------|----------|------------|----------|
| POST | `/api/fulfillment-requests/drafts` | 新建/DRAFT | DRAFT | 本人套餐；地址/需求去空白；草稿可放宽部分强校验但提交前必须满足 |
| PUT | `/api/fulfillment-requests/{id}/drafts` | DRAFT | DRAFT | 仅本人草稿 |
| POST | `/api/fulfillment-requests` | 新建 | PENDING_DISPATCH | A1–A3 全量预约契约 |
| POST | `/api/fulfillment-requests/{id}/submit` | DRAFT | PENDING_DISPATCH | 同上 |
| POST | `/api/fulfillment-requests/{id}/cancel` | DRAFT/PENDING_DISPATCH/PENDING_SERVICE | CANCELED | 释放占用；写操作日志 |
| POST | `/api/fulfillment-requests/{id}/customer-confirm` | PENDING_CONFIRM | 保持并标记 confirmed | 无 OPEN 异常 |
| POST | `/api/fulfillment-requests/{id}/exceptions` | PENDING_CONFIRM | EXCEPTION_PROCESSING | 写入 OPEN 异常；与确认互斥 |

### 2.3 运营动作

| 方法 | 路径 | 起始状态 | 成功后状态 | 关键校验 |
|------|------|----------|------------|----------|
| POST | `/api/fulfillment-requests/{id}/dispatch` | PENDING_DISPATCH | PENDING_SERVICE | 重校验套餐有效/剩余/人员技能状态/档期重叠；同事务双占用 |
| POST | `/api/fulfillment-requests/{id}/reject` | PENDING_DISPATCH | REJECTED | 写日志 |
| POST | `/api/fulfillment-requests/{id}/expire` | PENDING_DISPATCH/PENDING_SERVICE | EXPIRED | 释放占用 |
| POST | `/api/fulfillment-requests/{id}/start-service` | PENDING_SERVICE | IN_SERVICE | 档期占用可 CONVERTED |
| POST | `/api/fulfillment-requests/{id}/fulfillment-record` | IN_SERVICE | PENDING_CONFIRM | 后端算时长；记录完整 |
| POST | `/api/fulfillment-requests/{id}/fail` | PENDING_SERVICE/IN_SERVICE | FAILED | 释放占用 |
| POST | `/api/fulfillment-requests/{id}/exceptions/handle` | EXCEPTION_PROCESSING | 异常 HANDLED | 意见/责任/调整结果后端确认 |
| POST | `/api/fulfillment-requests/{id}/complete-settlement` | PENDING_CONFIRM 或 EXCEPTION_PROCESSING | COMPLETED | 无 OPEN 异常；已确认或异常路径已闭合；履约完整；占用转 CONVERTED；幂等 |

### 2.4 明确不做的接口

- 任意 `PATCH /status` 或通用状态编辑
- 前端提交最终扣次/金额并直接落库为权威结果
- 支付/退款/发票接口

---

## 3. 核心一致性规则（实现时落在领域服务）

1. **DispatchAtomicity**：`occupyQuota` + `occupySchedule` + `status→PENDING_SERVICE` 同事务；任一失败整单回滚。  
2. **ReleaseOnTerminal**：cancel/reject/expire/fail 将 VALID 占用改为 RELEASED，并回滚 `occupied_times`。  
3. **ConvertOnComplete**：结算完成将套餐占用 VALID→CONVERTED，`occupied_times-=N`，`used_times+=N`。  
4. **OverlapReject**：同一 staff 的 VALID 占用区间不得重叠；同一 package 未结束申请不得计划时段重叠。  
5. **CacheNotAuthority**：`available-staff` 可走 Redis；dispatch 必须 DB 重校验并在成功后 evict 缓存。

---

## 4. 前端页面与接口映射

| 页面/区域 | 主要接口 |
|-----------|----------|
| 履约申请列表与概览 | `GET /`、`GET /stats`；按状态展示可用动作 |
| 预约表单与人员选择 | `GET /api/my-packages`、`available-staff`、`POST /drafts` 或 `POST /` |
| 详情/派单/履约/异常结算 | `GET /{id}`、`dispatch`、`fulfillment-record`、`exceptions`、`exceptions/handle`、`settlement-preview`、`complete-settlement` |

页面状态：加载中、空数据、接口失败、无权限、额度不足、档期冲突 —— 均需可感知提示（对应后端业务错误码）。

---

## 5. Redis 缓存设计（方案 A）

| 项 | 设计 |
|----|------|
| Key | `staff:available:{serviceItemId}:{startEpoch}:{endEpoch}`（脚手架自动加个人前缀） |
| TTL | 180s |
| 读 | 命中返回；未命中查 DB 并回填 |
| 失效 | dispatch / cancel / reject / expire / fail / complete 后按相关 item+时段 evict |
| 降级 | Redis 异常时直接查 DB，不影响主链路 |
| 红线 | 派单事务内不读缓存做最终裁决 |

---

## 6. 测试设计锚点（供后续测试报告）

优先自动化覆盖的两个核心动作：

1. **dispatch**：正常成功双占用；余额不足失败；档期冲突失败；并发下不双占。  
2. **complete-settlement**：正常完成扣次；存在 OPEN 异常拒绝；重复调用不二次扣减。

至少 1 个核心领域方法的 JUnit（建议 `QuotaOccupationService.occupy/release/convert` 或状态机校验）。

---

## 7. 文档关系

| 文档 | 用途 |
|------|------|
| [01-frozen-assumptions.md](./01-frozen-assumptions.md) | 业务假设与状态机冻结 |
| 本文 | 表结构与接口设计 |
| [03-implementation-readiness.md](./03-implementation-readiness.md) | 开发门禁与实施清单 |
