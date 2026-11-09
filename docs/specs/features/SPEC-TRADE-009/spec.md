# SPEC-TRADE-009 退款审核与状态机

## 1. 基本信息

| 项目 | 内容 |
|---|---|
| 编号 | SPEC-TRADE-009 |
| 标题 | 退款审核与状态机 |
| 来源 | D6、engineering-plan 2.3 与 4.4、审查结论 A6 |
| 状态 | 已完成 |
| 计划层级 | — |
| 最后核对 | 2026-11-09 |
| AC 完成度 | 7/7 |

## 2. 背景与目标

退款不是用户自助完成的动作，而是「用户申请 → 运营审核 → 原渠道退款」两段式流程。缺少显式状态机时，重复点击审核、审核超时重试与渠道回调重入都会造成二次退款。本条目冻结退款单的六个状态、状态迁移的唯一入口与「先落状态再调渠道」的顺序，保证任一退款单在同一时刻只有一种确定状态，且退款金额只可能支付一次。渠道调用失败分为两类：渠道明确拒绝属于确定失败，可以安全放回可再次申请；调用超时或响应不可解析属于结果未知，渠道可能已退款，必须停在不可再次申请的状态等待人工核对账单。

## 3. 范围

### 3.1 包含

- `refund_request` 的状态定义与全部合法迁移。
- 运营审核入口 `POST /api/v1/admin/refunds/{refundId}/review` 及内部接口 `POST /internal/v1/refunds/{refundId}/review`。
- 审核通过后的渠道调用顺序、结果回写与订单状态联动。
- 同一订单同时仅允许一条进行中退款单的并发约束。

### 3.2 范围外

- 退款准入判定（7 天 / 进度 ≤ 20%）与退款单创建，见 SPEC-TRADE-008。
- 渠道异常、失败重试与重新申请，见 SPEC-TRADE-010。
- 退款成功后的权益撤销与学习记录处理，见 SPEC-TRADE-011。
- 支付宝渠道 SDK 的签名与请求细节，属于实现层，不在本条约定。

## 4. 用户场景

| 编号 | 类型 | 场景 | 期望结果 |
|---|---|---|---|
| S-1 | 正常 | 运营在后台对一条待审核退款单点击通过 | 退款单先变为「调渠道中」，渠道成功后再变为「已退款」，订单变为已退款 |
| S-2 | 正常 | 运营驳回并填写 80 字原因 | 退款单变为「驳回」，订单回到已支付，原因可在用户侧查询 |
| S-3 | 边界 | 两名运营同时对同一条待审核单点击通过 | 只有一次请求把状态从 0 改为 1 并调用渠道，另一次返回当前状态且不触发渠道调用 |
| S-4 | 异常 | 对已退款（状态 2）的单再次点击通过 | 返回已存结果，状态保持 2，不产生第二笔渠道退款 |
| S-5 | 异常 | 驳回时未填写原因或原因超过 300 字 | 返回 `40001`，退款单状态保持 0 |

## 5. 验收标准

- AC-1：`refund_request.status` 的取值只允许 0 待审核、1 调渠道中、2 已退款、3 驳回、4 退款失败、5 结果未知待确认，任何写入其他值的请求被拒绝且不落库。
- AC-2：审核通过时，服务端先把 `status` 从 0 更新为 1 并提交事务，之后才调用渠道退款接口，渠道调用期间该单对其他请求可见为状态 1。
- AC-3：渠道返回退款成功后，退款单 `status` 置为 2，`trade_order.status` 置为 5 已退款，`payment_record.status` 置为 3 已退款，三者同一次事务内完成。
- AC-4：驳回时 `review_reason` 必填且长度为 1 至 300 字符，退款单 `status` 置为 3，`trade_order.status` 回到 1 已支付；原因为空或超限时返回 `40001` 且状态保持不变。
- AC-5：对 `status` 不为 0 的退款单再次提交审核，接口返回该单当前状态且不调用渠道、不改变任何金额字段。
- AC-6：同一订单已存在 `status` 为 0、1、2 或 5 的退款单时，再创建退款单因唯一约束 `uk_active_order` 失败，接口返回既有退款单而不是 500。
- AC-7：渠道调用抛出非业务异常（超时、连接异常、响应不可解析）时，退款单 `status` 置为 5 结果未知待确认并在 `fail_reason` 写入 `result unknown:` 前缀与异常类型，`trade_order.status` 保持 4 退款中，该订单不得回到可再次申请退款的状态。

## 6. 边界与异常

| 编号 | 触发条件 | 系统表现 | 关联 AC |
|---|---|---|---|
| E-1 | 退款单不存在 | 返回 `40401`，不写审计 | AC-1 |
| E-2 | 调用方角色不含 `ADMIN` 或 `OPS` | 返回 `40301`，退款单状态不变 | AC-1 |
| E-3 | 订单当前不是已支付状态 | 返回 `62001`，退款单状态不变 | AC-2 |
| E-4 | 渠道明确拒绝（返回业务错误码或拒绝退款） | 退款单置为 4，`fail_reason` 以 `channel rejected:` 开头，订单回到 1 已支付，用户可重新申请（见 SPEC-TRADE-010） | AC-2 |
| E-5 | 渠道调用超时、连接重置或响应不可解析 | 退款单置为 5，`fail_reason` 以 `result unknown:` 开头，订单保持 4 退款中，用户不能重新申请，按 6.2 人工处置 | AC-7 |
| E-6 | 渠道成功但本地回写事务失败 | 以渠道退款单号 `uk_channel_refund` 唯一约束承接重放，重试时补写 2 而不是再次发起退款 | AC-3 |
| E-7 | 审核人与申请人相同 | 不拦截，仅记录 `reviewed_by`，审计可追溯 | AC-5 |
| E-8 | 进程在「状态 1 已提交、渠道尚未调用」之间崩溃 | 无自动重入：该单停留在状态 1，由人工按 6.1 的 SQL 识别并处置 | AC-2 |

### 6.1 状态 1 超时人工处置

状态 1 表示「已提交审核决定、正在调用渠道」。若进程在提交状态 1 后、渠道调用返回前崩溃，退款单会停留在 1，订单停留在 4（退款中），既不会自动完成也不会自动失败。当前版本不引入自动补偿任务，按以下 SQL 识别并人工处置（建议阈值 10 分钟）：

```sql
SELECT r.id AS refund_id, r.refund_no, r.order_id, o.order_no,
       r.amount, r.reviewed_by, r.reviewed_at
FROM refund_request r
INNER JOIN trade_order o ON o.id = r.order_id
WHERE r.status = 1
  AND r.reviewed_at < NOW() - INTERVAL 10 MINUTE
  AND r.deleted = 0
ORDER BY r.reviewed_at;
```

处置口径：先按 `refund_no` 到渠道查询是否存在退款成功的流水。渠道已退款则把退款单置 2、订单置 5、支付单置 3；渠道未退款则把退款单置 4 并写 `fail_reason = 'manual: timeout in status 1'`、订单回到 1。两条路径均需在人工操作记录中留痕。

本节只覆盖「崩在状态 1、渠道是否被调用都无法确认」这一种情况；调用已经发出但结果未知的单据走 6.2，两者的识别 SQL 与处置口径互不替代。

### 6.2 调用结果未知人工处置

状态 5 表示「审核已通过、渠道调用已经发出，但没有拿到可判断的结果」（超时、连接重置、响应不可解析）。此时渠道可能已经把款项退回，因此订单保持 4 退款中，**不得**放回 1 已支付，用户也不能再次申请。按以下 SQL 识别：

```sql
SELECT r.id AS refund_id, r.refund_no, r.order_id, o.order_no,
       r.amount, r.reviewed_by, r.fail_reason, r.reviewed_at
FROM refund_request r
INNER JOIN trade_order o ON o.id = r.order_id
WHERE r.status = 5
  AND r.deleted = 0
ORDER BY r.reviewed_at;
```

处置口径：按 `refund_no` 到渠道核对账单，二选一并留痕。

```sql
-- 路径一：渠道已退款
UPDATE refund_request SET status = 2, channel_refund_no = ?, refunded_at = NOW(3)
WHERE id = ? AND status = 5;
UPDATE trade_order SET status = 5 WHERE id = ? AND status = 4;
UPDATE payment_record SET status = 3 WHERE id = ? AND status = 1;
-- 权益撤销沿用 entitlement_outbox（action=1），按 SPEC-TRADE-011 补发

-- 路径二：渠道未退款
UPDATE refund_request SET status = 4, fail_reason = 'manual: not refunded at channel'
WHERE id = ? AND status = 5;
UPDATE trade_order SET status = 1 WHERE id = ? AND status = 4;
```

路径一完成后用户按已退款处理；路径二完成后订单恢复可重新申请，仍受 SPEC-TRADE-008 的 7 天与进度约束。

## 7. 依赖的规格条目

| 编号 | 依赖内容 | 依赖方向 |
|---|---|---|
| SPEC-TRADE-008 | 退款单创建前的准入校验与金额确定 | 本条目依赖对方 |
| SPEC-TRADE-010 | 状态 4 与 5 之后的失败处置、补偿与重新申请 | 对方依赖本条目 |
| SPEC-TRADE-011 | 状态 2 之后权益失效与学习记录保留 | 对方依赖本条目 |
| SPEC-PLAT-001 | 运营角色 `ADMIN` / `OPS` 的可信身份来源 | 本条目依赖对方 |

## 8. 未决问题

| 编号 | 待定事项 | 影响 | owner | 结案条件 | 状态 |
|---|---|---|---|---|---|
| Q-1 | [待定] 渠道已成功但本地回写持续失败时，是否需要独立的人工补偿工单入口 | 影响失败退款单的处置时效与对账口径 | 负责人 | 明确补偿入口归属（后台页面或运维脚本）并登记新条目后关闭 | 未决 |
| Q-2 | [待定] 状态 5 目前只有 6.2 的处置 SQL，是否在运营后台提供「确认已退款 / 确认未退款」入口 | 影响未知结果的处置效率与误操作风险；真实渠道接入前只是本地演练 | 负责人 | 真实渠道接入前明确入口形态（后台接口或运维脚本）并登记新条目后关闭 | 未决 |

## 9. 关联

- 上游决策：D6（7 天 / 进度 ≤ 20% 全额退款）、D11（支付截止与渠道固定）。
- 关联条目：SPEC-TRADE-005、SPEC-TRADE-008、SPEC-TRADE-010、SPEC-TRADE-011。
- 实现索引：`docs/project-status.md` 的规格索引表。
