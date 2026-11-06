# 课程平台 · 本地可运行初版

一条命令拉起 MySQL、Redis、MinIO、全部后端服务、学员 Web 与运营后台，并导入演示数据。
用于本地联调与主链路演示，不包含真实支付、退款、对账、AI 能力与微信支付。

## 前置依赖

| 依赖 | 版本 | 说明 |
|---|---|---|
| Docker Desktop（含 compose v2） | 最新稳定版 | 运行全部容器 |
| JDK | 17 | dev-up 在宿主机构建 jar |
| Node.js + pnpm | Node 20 / pnpm 9 | 构建 `apps/web`、`apps/admin` |
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

## 初版不包含

退款与对账、真实支付渠道、微信支付、AI 服务接入、UGC 功能、小程序端发布流程。
