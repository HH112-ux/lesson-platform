# 课程平台 · 本地可运行初版

一条命令拉起 MySQL、Redis、MinIO、全部后端服务、学员 Web 与运营后台，并导入演示数据。
用于本地联调与主链路演示，包含模拟支付、退款与权益演练；真实支付宝渠道、对账、AI 与 UGC 能力不在本地栈范围，见文末「已知限制」。

## 前置依赖

| 依赖 | 版本 | 说明 |
|---|---|---|
| Docker Desktop（含 compose v2） | 最新稳定版 | 运行全部容器 |
| JDK | 17 | dev-up 在宿主机构建 jar |
| Node.js + pnpm | Node 22 / pnpm 9 | 构建 `apps/web`、`apps/admin`、`apps/miniapp` |
| jq | 1.6+ | 冒烟脚本解析响应 |

首次使用执行一次 `corepack enable pnpm`。

## 启动与停止

```bash
bash scripts/dev-up.sh     # 构建 jar 与前端 → 起容器 → Flyway 迁移 → 导入种子 → 建 MinIO 桶
bash scripts/dev-down.sh   # 停栈并释放端口（保留数据卷）
bash scripts/dev-down.sh --volumes   # 连数据卷一起清空
```

`dev-up.sh` 会自动生成 `infra/jwt` 下的本地 RS256 密钥、复制 `infra/.env.example` 为 `infra/.env`。

## 演示账号

密码统一为 `Demo@123456`（仅本地演示，禁止用于任何线上环境）。

| 账号 | 角色 | 用途 |
|---|---|---|
| `demo_teacher_lin` | TEACHER | 建课、编辑、提交审核 |
| `demo_teacher_su` | TEACHER | 同上 |
| `demo_ops` | OPS | 审核队列、上架/驳回 |
| `demo_buyer` | STUDENT | 浏览、下单、支付、学习 |
| `demo_admin` | ADMIN | 运营看板与用户/订单查询 |

种子包含：两名教师各 3–5 门课（含草稿、待审核、已上架各一）、已上架课程含章节课时与视频占位对象、一笔已支付订单。

## 访问地址

| 入口 | 地址 |
|---|---|
| 学员 Web | http://localhost:5173 |
| 运营后台 | http://localhost:5174 |
| 网关 | http://localhost:8080 |
| 认证 / 课程 / 交易 / 学习 / 互动 / 运营 / AI | http://localhost:8101 … 8107 |
| MinIO API / 控制台 | http://localhost:9000 / http://localhost:9001 |

数据库端口沿用 `infra/.env` 的 `LP_MYSQL_PORT`（本机为 3307），容器内服务通过 `mysql:3306` 互访。

## 一条命令验收

```bash
bash scripts/verify-all.sh
```

依次执行：起栈（`dev-up.sh`）→ 冒烟（`smoke.sh`）→ outbox 故障演练 → 退款闭环演练 → 清理演练数据，最后输出每一步的 `PASS` / `FAIL` / `SKIPPED` 与总耗时。任一步失败立即停止，打印失败步骤的日志路径与末尾 40 行，并保留演练数据供排查；全部通过则删除演练创建的课程、订单、支付、退款、权益、进度与审计行，可重复执行。

日志写入 `logs/verify-<时间戳>/<步骤>.log`（已被 `.gitignore` 忽略）。栈已在运行时可用 `LP_VERIFY_SKIP_UP=1 bash scripts/verify-all.sh` 跳过起栈，`dev-up` 一步记为 `SKIPPED`。

## 冒烟

```bash
# 先在 infra/.env 中打开模拟支付（默认关闭）
LP_PAY_MOCK_ENABLED=true

bash scripts/dev-up.sh
bash scripts/smoke.sh
```

`smoke.sh` 顺序验证：教师建课 → 新增章节/课时 → 提交审核 → 运营审核上架 → 学员浏览 → 下单 → 支付 → 权益 → 播放地址 → 进度回写，全部通过输出 `SMOKE OK`；任一步失败立即停止并打印响应体。模拟支付关闭时支付及后续步骤输出 `SKIPPED`，不会输出 `SMOKE OK`。

## 权益 outbox 故障演练

`scripts/outbox-fault-drill.sh` 真实停掉 `learn-service`，验证「订单成功 → outbox 重试 → 恢复补发 → 幂等 → 死信重放」整条链路，而不仅是单测。

```bash
bash scripts/dev-up.sh          # 至少包含 mysql/auth/course/trade/learn/admin/gateway
bash scripts/outbox-fault-drill.sh
```

前置：本机有 `jq` 与 `docker`，栈已启动；脚本会临时用 `LP_PAY_MOCK_ENABLED=true` 重建 `trade-service`，结束（含失败退出）时按 `infra/.env` 恢复，不改文件。每次运行会新建一门演练课程，并在开始时清掉上一轮的 `DRILL-*` outbox 行。

脚本逐步打印实际观测值，任一断言失败立即退出并打印 HTTP 状态与响应体；全部通过输出：

```
DRILL OK: order stayed paid during the outage, retries backed off, delivery recovered, replay was idempotent, dead letter was requeued.
```

覆盖的九步：

| 步骤 | 断言 |
|---|---|
| 1 | 网关健康；模拟支付可用；清理上一轮演练数据 |
| 2 | 教师建课、提交，运营审核上架 |
| 3 | `learn-service` 已停止 |
| 4 | 下单与支付回调仍 200，订单与订单详情均为已支付，outbox 落 `status=0` |
| 5 | `retry_count>=1`、`next_retry_at` 在未来、未被误标死信，`pending` 计数上升 |
| 6 | 恢复 `learn-service` 后自动补发：`status=1`、`delivered_at` 有值、权益恰好 1 行、我的课程可见、`pending` 回落 |
| 7 | 同一权益重复投递 3 次，`uk_user_course` 兜住，权益仍 1 行 |
| 8 | 构造 `retry_count=9` 的行，失败后进入死信（`status=2`、`retry_count=10`），失败列表与 `failed` 计数可见 |
| 9 | 重放接口把死信救回，补发成功，`failed` 计数回落 |

失败时先看脚本最后打印的步骤号与 HTTP 响应体，再查 `docker logs lp-trade-service`；outbox 原始状态可直接查 `lp_oms.entitlement_outbox`。

### outbox 指标抓取

`trade-service` 直接暴露 Prometheus 文本端点（服务端口 8103，不经网关；网关与 nginx 只暴露自身 actuator，不会转发下游服务的 `/actuator/prometheus`）。actuator 仅开放 `health`、`info`、`prometheus` 三项。

```bash
curl -s http://localhost:8103/actuator/prometheus | grep entitlement_outbox
```

| 指标 | 含义 |
|---|---|
| `entitlement_outbox_pending` | 待投递的权益记录数；持续大于 0 说明中继未追平或下游不可用 |
| `entitlement_outbox_failed` | 超过重试上限、需要人工重放的死信数；大于 0 应告警 |

两个 Gauge 在中继每批投递后与每 15 秒刷新，抓取端不需要访问数据库。

## 退款闭环演练

`scripts/refund-drill.sh` 在本地真实栈上验证「下单 → 支付 → 申请退款 → 退款成功 → 权益失效 → 播放被拒」整条链路，而不只是单测。

```bash
bash scripts/dev-up.sh
bash scripts/refund-drill.sh
```

前置与 outbox 演练相同（`jq`、`docker`、栈已启动）；脚本同样会临时用 `LP_PAY_MOCK_ENABLED=true` 重建 `trade-service` 并在退出时恢复。每次运行新建自己的课程与订单，可重复执行。

口径：退款成功后权益置为**失效**（`user_entitlement.status=1`，软状态，不物理删除），学习记录保留；已购用户再次访问该课程按未购处理，「我的课程」中该课程标记 `refunded=true`，播放地址接口返回 `40302`。再次购买同一课程会复用同一行并重新生效。

| 步骤 | 断言 |
|---|---|
| 1 | 网关健康；模拟支付可用 |
| 2 | 教师建课、提交，运营审核上架 |
| 3 | 下单支付成功，权益已发放，退款前可播放 |
| 4 | 退款成功（`refundNo=RF{orderNo}`），订单置 `status=5`，`refund_request` 一行，撤销 outbox 已投递 |
| 5 | 权益行仍在且 `status=1`、`revoked_at` 有值；我的课程 `refunded=true`；播放返回 `40302` |
| 6 | 重复退款返回同一 `refundNo`，退款行仍 1 条，撤销 outbox 仍 1 条，`revoked_at` 不变 |
| 7 | 另一笔订单退款金额超过实付返回 `62018`，不产生退款行且订单状态不变 |

全部通过输出 `REFUND OK`；失败时先看步骤号与响应体，再查 `lp_oms.refund_request`、`lp_oms.entitlement_outbox`（`action=1`）与 `lp_lms.user_entitlement`。

退款决策：渠道退款同步执行（买家需要确定结果，`refund_no` 即渠道幂等键，重试返回原退款）；权益撤销走 outbox（跨服务副作用可重试，复用同一套退避、死信与运营重放，不引入新中间件）。

## 本地模拟支付

- 开关：`infra/.env` 的 `LP_PAY_MOCK_ENABLED`，**默认 `false`**。
- 打开后，`trade-service` 用 `MockPaymentGateway` / `MockSignatureVerifier` 替换未配置的真实渠道适配器，接口与真实渠道相同。
- 回调仍是 `GET /api/v1/payments/mock/callback` 走 `PaymentService.handleNotify`：HMAC-SHA256 验签、渠道校验、金额校验、幂等、权益发放一个都不少；只是签名方在本机（`LP_PAY_MOCK_SECRET`）。
- 验签失败、篡改金额或缺少签名返回 `62016`，订单不会变为已支付。
- `prod` / `staging` profile 下把开关设为 `true`，服务**启动直接失败**并提示不允许。

## 切换到真实支付

1. `LP_PAY_MOCK_ENABLED=false`。
2. 按真实渠道接入替换 `UnconfiguredPaymentGateway` 与 `UnconfiguredSignatureVerifier`（支付宝应用私钥、公钥证书、异步通知地址），密钥只经环境变量或 K8s Secret 注入。
3. 真实凭据未配置时，下单后的支付请求返回 `50205`，不会伪造成功。

## 常见问题

| 现象 | 处理 |
|---|---|
| 端口被占用 | `bash scripts/dev-down.sh`；或改 `infra/.env` 中对应端口 |
| 想清空数据重来 | `bash scripts/dev-down.sh --volumes && bash scripts/dev-up.sh` |
| 冒烟提示 `SKIPPED` | `infra/.env` 打开 `LP_PAY_MOCK_ENABLED=true` 后重启 |
| 播放地址无法打开 | `dev-up.sh` 使用宿主机局域网 IP 生成预签名地址；确认手机/浏览器与宿主机同网段，或改用 `127.0.0.1` 访问 |
| 服务起不来 | `docker compose -f infra/docker-compose.yml logs <service>` 查看日志 |

## 文档索引

| 文档 | 用途 |
|---|---|
| [AGENTS.md](./AGENTS.md) | 分支、提交、评审、发布与命名规则 |
| [product-lifecycle.md](./docs/product-lifecycle.md) | 产品范围、角色权限、业务规则与验收口径 |
| [engineering-plan.md](./docs/engineering-plan.md) | 架构、数据模型、接口与部署 |
| [project-status.md](./docs/project-status.md) | 能力实现状态、SPEC 索引与计划层级 |
| [docs/specs](./docs/specs/) | 各域规格条目索引与 AC 摘要 |
| [local-dev-guide.md](./docs/local-dev-guide.md) | 本地开发与联调细节 |
| [purchase-flow.md](./docs/purchase-flow.md) | 去网页购买链路与当前缺口 |
| [changelog.md](./docs/changelog.md) | 变更记录 |

## 已知限制

| 项 | 当前状态 | 计划层级 |
|---|---|---|
| 真实支付宝渠道 | 仅模拟支付（`LP_PAY_MOCK_ENABLED`）；真实凭据未配置时支付返回 `50205` | 接入真实收款前完成 |
| 对账 | 未实现；未完成对账能力不得开启真实收款 | 真实收款前置 |
| AI 能力 | 未接入 | 上线后首批 |
| UGC（评论、收藏、举报） | 未实现 | 上线后首批 |
| 微信小程序 | 已有登录、课程、我的课程与播放；去网页购买仅复制链接，未接入 ticket 签发与二维码 | 见 [purchase-flow](./docs/purchase-flow.md) |
| 小程序发布流程 | 不在本地栈范围 | 上线阶段 |
