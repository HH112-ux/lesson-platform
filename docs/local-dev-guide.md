# 本地开发指南

> 适用范围：在本地启动依赖中间件、后端网关、Web 前端与微信小程序。命令与代码用英文，说明用中文。
> 目录、分支、提交与命名的强制规则见 `AGENTS.md`；本指南只描述"怎么跑起来"。

## 0. Git 远端与 SSH 配置

本机存在指向 `127.0.0.1:7897` 的系统级代理（TUN 模式），会劫持部分场景下的 `git push/pull`。经过实测，稳定方案如下。

### 0.1 最终方案：SSH over HTTPS（ssh.github.com:443）

在 `~/.ssh/config` 中加一段（已生效）：

```sshconfig
Host github.com
    HostName ssh.github.com
    Port 443
    User git
    IdentityFile ~/.ssh/id_ed25519
    IdentitiesOnly yes
    AddKeysToAgent yes
    UseKeychain yes
```

仓库 remote 设置为 SSH：

```bash
git remote set-url origin git@github.com:HH112-ux/lesson-platform.git
git ls-remote origin          # 应正常列出 refs，无 Connection closed
git push -u origin <branch>   # 应直接 push，无需密码
```

### 0.2 判定方法

检查 22 直连是否被代理劫持：

```bash
nc -vz -G 5 github.com 22     # 若 succeeded 但 git ls-remote 仍报 Connection closed，
                                # 说明 shell 直连能过但 git 子进程被 TUN 劫持
```

两个端口都通时，优先用 `ssh.github.com:443` —— 它走 HTTPS 协议通道，系统层 TUN 不会拦截，git 子进程也能稳定通过。

### 0.3 备选：HTTPS + gh 凭据助手

若 SSH 通道彻底不通，可切换为 HTTPS + `gh auth setup-git`：

```bash
git remote set-url origin https://github.com/HH112-ux/lesson-platform.git
gh auth setup-git            # 注册 gh auth git-credential 作为凭据助手
```

实测 `git fetch / ls-remote / push` 均通过；比 SSH 稍慢、且 remote URL 更长，仅作备选。

### 0.4 ~/.ssh/config 备份与恢复

`~/.ssh/config.bak-<时间戳>` 保留每次改动前的备份，便于回滚；不要动与 GitHub 无关的 Host 段（如 `colima`）。

## 1. 前置条件

| 工具 | 版本要求 | 用途 | 校验命令 |
|---|---|---|---|
| JDK | 17 及以上 | 后端编译与运行 | `java -version` |
| Maven | 使用仓库内 `./mvnw` | 后端构建 | `./mvnw -v` |
| Node.js | 22.13 及以上 | 前端构建、小程序静态校验 | `node -v` |
| pnpm | 11.22.0（根 `package.json` 的 `packageManager` 锁定） | 前端依赖管理 | `pnpm -v` |
| Docker Desktop | 最新稳定版 | `infra/` 五件套 | `docker --version` |
| 微信开发者工具 | 最新稳定版 | 小程序预览与真机调试 | 应用内版本号 |

Node 与 pnpm 版本以根目录 `package.json` 的 `packageManager` 字段为准，Node 版本同时写入 `.nvmrc`（22），与 CI 的 `actions/setup-node` 保持一致。

### 1.1 第一天：启用仓库钩子（必做一次）

```bash
bash scripts/install-hooks.sh   # 等价于 git config core.hooksPath .githooks
```

启用后每次 `git commit` 会执行：

| 钩子 | 行为 |
|---|---|
| `pre-commit` | 始终跑 `scripts/check-docs.sh`；**暂存区含 `*.java` 时**再跑 `timeout 180 ./mvnw -B -q spotless:check` |
| `commit-msg` | 跑 `scripts/check-commit-msg.sh` 校验提交信息格式 |

要点：

- Java 格式门禁的 ratchet 基线是 `origin/develop`，钩子会先 `git fetch --quiet origin develop`；fetch 失败只打印一行警告、不阻断提交，CI 仍会做最终判定。
- 校验只检查不修改：失败时列出本次暂存的 Java 文件并提示 `./mvnw spotless:apply`，修完必须重新 `git add` 再提交；钩子不会自动格式化，也不会自动暂存。
- 只暂存 `*.md` 或前端文件时立即通过，不触发 Maven。
- `--no-verify` 属于需要说明理由的例外，不能作为常态；钩子未启用也不能作为跳过 CI 的理由。

## 2. 仓库结构与当前状态

```text
AGENTS.md              仓库级工作规则（开工必读）
db/                    Flyway 迁移脚本（已存在）
docs/                  工程规划与产品文档（已存在）
infra/                 本地依赖编排（已存在）
lp-common/             后端公共模块（已存在）
lp-gateway/            网关模块（已存在）
scripts/               仓库脚本（已存在）
apps/web/              C 端 Web 骨架（已存在）
apps/miniapp/          微信小程序骨架（已存在）
apps/admin/            运营与教师后台骨架（已存在，端口 5174）
packages/shared/       Web 与 admin 共用类型与常量（已存在，`@lp/shared`）
lp-auth-service/       认证域服务骨架（已存在，端口 8101）
lp-course-service/     课程域服务骨架（已存在，端口 8102）
lp-trade-service/      交易域服务骨架（已存在，端口 8103）
lp-learn-service/      学习域服务骨架（已存在，端口 8104）
lp-ugc-service/        互动域服务骨架（已存在，端口 8105）
lp-admin-service/      管理域服务骨架（已存在，端口 8106）
lp-ai-service/         AI 域服务骨架（已存在，端口 8107）
```

## 3. 启动本地依赖（infra 五件套）

```bash
cp infra/.env.example infra/.env
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps
```

首次启动会由 MySQL 容器自动执行 `infra/mysql/init/00-create-databases.sql`，创建 `lp_ums`、`lp_cms`、`lp_oms`、`lp_lms`、`lp_ugc`、`lp_ai`、`lp_ops` 七个库与业务账号。

| 组件 | 端口 | 控制台或入口 |
|---|---|---|
| MySQL | 3306（默认） / 3307（本机覆盖） | `mysql -h127.0.0.1 -P{port} -uroot -p` |
| Redis | 6379 | 容器内 `redis-cli ping` |
| RocketMQ NameServer | 9876 | 宿主 `mqadmin clusterList -n 127.0.0.1:9876` |
| RocketMQ Broker | 10909 / 10911 | 无 |
| MinIO | 9000 / 9001 | `http://localhost:9001` |
| Nacos | 8848 / 9848 | `http://localhost:8848/nacos` |

```bash
# 停止容器，保留数据卷
docker compose -f infra/docker-compose.yml down
# 停止并清空数据卷（会丢本地数据，慎用）
docker compose -f infra/docker-compose.yml down -v
```

当前 `infra/` 只有编排文件；`infra/up.sh`、`infra/down.sh`、`infra/reset.sh`、`infra/init/*` 仍为 M0 规划项（见工程规划 13.6），未创建前以上面的 compose 命令为准。

### 3.1 本机已知端口占用与验证路径

这些是当前开发机的实测结论，其他机器可能不同；遇到"容器起来了但连不上"先检查这几条：

- **MySQL 端口冲突**：宿主机原生 `/usr/local/mysql/bin/mysqld` 占用 `3306`，`infra/.env` 已覆盖 `LP_MYSQL_PORT=3307`；连接串一律用 `3307`，不要硬写 `3306`。本机重建 MySQL 容器时务必 `docker compose -f infra/docker-compose.yml down -v mysql` 后再 `up -d mysql`，让 compose 重新读取 `.env`。
- **Redis 端口冲突**：宿主机原生 `redis-server` 占用 `6379`，compose 无法抢占；宿主机直连 `redis-cli ping` 命中的是原生实例而非容器。容器内验证：`docker exec lp-redis redis-cli ping` 应返回 `PONG`。
- **RocketMQ `brokerIP1` 副作用**：compose 的 broker.conf 写了 `brokerIP1=127.0.0.1`，这是让**宿主**客户端能连上的必要设置。但容器内执行 `mqadmin clusterList -n 127.0.0.1:9876` 会看到 `RemotingConnectException`（broker 注册地址在容器内指向自身），属预期；mqadmin 验证统一从宿主发起。
- **MinIO 有两个视角的地址，混用会 `connection refused`**：`LP_MINIO_PUBLIC_ENDPOINT`（宿主机 LAN IP）只给宿主机侧的浏览器和播放器用；容器内的 course-service 在确认上传时要回读对象，必须走 compose 网络地址 `http://minio:9000`，compose 已默认注入 `LP_COURSE_UPLOAD_MINIO_ENDPOINT`。把容器内端点写成宿主机 LAN IP 会失败，因为 compose 只把 MinIO 发布到 `127.0.0.1`，容器访问该地址会 `connection refused`。
- **预签名上传 URL 的主机名就是 `minio:9000`**：宿主机上的 `scripts/smoke.sh`、`scripts/outbox-fault-drill.sh`、`scripts/refund-drill.sh` 用 `curl --resolve minio:9000:127.0.0.1` 保留签名 Host 直连回环端口；在浏览器里走教师上传则需要在宿主机 `/etc/hosts` 增加 `127.0.0.1 minio`，否则该域名无法解析。
- **course-service 运行时镜像自带静态 `ffprobe`**：上传确认要探测视频，镜像用多阶段构建从 `mwader/static-ffmpeg` 复制单个二进制。Docker Hub 不可达时在 `infra/.env` 覆盖 `LP_FFPROBE_IMAGE` 为可用镜像地址即可，compose 默认值不需要改。


## 4. 后端

```bash
# 全量构建（跳过单测）
./mvnw -B -DskipTests package

# 运行单测
./mvnw -B test

# 启动网关
java -jar lp-gateway/target/lp-gateway-0.1.0-SNAPSHOT.jar
curl http://localhost:8080/actuator/health
```

说明：

- 网关监听 8080，`/actuator/health` 返回 `UP` 即代表骨架可用。
- 七个下游服务已在 8101–8107 提供 `/actuator/health`，但业务接口尚未实现；访问对应业务路由返回 404 属预期，不影响本地起停验证。
- 端口被占用时用 `lsof -i :8080` 定位后释放。
- **course / learn / trade 三个服务启动前必须设置 `LP_INTERNAL_TOKEN`**（无默认值，缺失会直接启动失败）：

```bash
export LP_INTERNAL_TOKEN="local-internal-token"
```

`/internal/v1/**` 全部要求请求头 `X-Internal-Token` 与该值一致；脚本或手工 curl 调内部接口时需显式带上，例如 `-H "X-Internal-Token: $LP_INTERNAL_TOKEN"`。

- 免费试听开关：`LP_FREE_PREVIEW_ENABLED`（默认 `true`）。设为 `false` 后免费课时也必须先购买，否则播放与进度接口返回 `40302`。

### 4.1 认证密钥与登录链路（M1）

```bash
# 生成 RS256 密钥对，写入 infra/jwt（已被 .gitignore 忽略，禁止提交）
bash scripts/gen-jwt-keys.sh

# 认证服务与网关读取同一对密钥，按脚本输出的 export 行配置
export LP_JWT_PRIVATE_KEY_PATH="$PWD/infra/jwt/jwt-private.pem"
export LP_JWT_PUBLIC_KEY_PATH="$PWD/infra/jwt/jwt-public.pem"

# 认证服务（8101）与网关（8080）各起一个终端
./mvnw -q -pl lp-auth-service spring-boot:run
./mvnw -q -pl lp-gateway spring-boot:run
```

登录与鉴权验证：

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"account":"alice","password":"secret123"}'

# 轮换：用上一步返回的 refreshToken 换新的一对 token，旧 refresh 立即失效
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refreshToken>"}'

# 注销：吊销当前 refresh，重复调用仍返回成功
curl -s -X POST http://localhost:8080/api/v1/auth/logout \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refreshToken>"}'

# 受保护路径缺少 token 返回 40101，token 过期返回 40102
curl -s http://localhost:8080/api/v1/orders

# 公开目录：未登录可访问（可选鉴权），带 token 时返回 owned 标记
curl -s 'http://localhost:8080/api/v1/categories'
curl -s 'http://localhost:8080/api/v1/courses?category=design&keyword=插画&page=1&size=20'
curl -s 'http://localhost:8080/api/v1/courses/1' \
  -H "Authorization: Bearer <accessToken>"
```

- 账号需先写入 `lp_ums.sys_user`（密码为 BCrypt 哈希）；M1 当前已实现注册、登录、刷新、退出与小程序 code 登录（默认关闭），找回密码与更多第三方登录在后续切片。
- 登录、刷新与退出依赖 Redis：key 为 `lp:auth:refresh:{sha256(token)}`，TTL 与 refresh 有效期一致（7 天），不落明文 token；Redis 不可用返回 `50001`，不会放行未校验的 token。
- `lp.auth.jwt.private-key-path` 或 `lp.identity.jwt.public-key-path` 为空时服务启动失败，属于预期的保护行为。

### 4.2 小程序 code 登录（M1，默认关闭）

开关关闭时 `POST /api/v1/auth/miniapp-login` 一律返回 `66003`，不需要任何微信配置即可安全启动。

```bash
# 有 AppSecret 时：secret 只经环境变量下发，禁止写入 application.yml 或仓库
export LP_MINIAPP_APP_ID="<小程序 AppID>"
export LP_MINIAPP_APP_SECRET="<小程序 AppSecret>"
export LP_MINIAPP_LOGIN_ENABLED=true
export LP_MINIAPP_LOGIN_MOCK_ENABLED=false
```

```bash
# 无 AppSecret 的本地联调：用 mock 客户端，同一 code 始终映射同一 openid
export LP_MINIAPP_LOGIN_ENABLED=true
export LP_MINIAPP_LOGIN_MOCK_ENABLED=true
./mvnw -q -pl lp-auth-service spring-boot:run

# 首次登录 isNewUser=true，重复登录同一 code 时 isNewUser=false
curl -s -X POST http://localhost:8080/api/v1/auth/miniapp-login \
  -H 'Content-Type: application/json' -d '{"code":"local-code"}'
```

- 配置项：`lp.auth.miniapp.enabled`（默认 `false`）、`mock-enabled`（默认 `false`）、`app-id`（占位符 `${LP_MINIAPP_APP_ID}`）、`app-secret`（占位符 `${LP_MINIAPP_APP_SECRET}`）。
- `session_key` 只落库供服务端使用，不写日志、不回传；`mock-enabled` 禁止用于 staging/prod。
- 真实 `jscode2session` 联调待 AppSecret 到位后进行。

### 4.3 交易域 outbox 指标与死信接口（本地）

交易服务（8103）启动后即可查看权益投递积压情况：

```bash
# 待投递数、放弃数（Gauge，中继每批后与每 15 秒刷新）
curl -s http://localhost:8103/actuator/prometheus | grep entitlement_outbox

# 列出放弃投递的记录（不经网关，直接打服务端口；需内部令牌）
curl -s -H "X-Internal-Token: $LP_INTERNAL_TOKEN" \
  'http://localhost:8103/internal/v1/entitlement-outbox/failed?pageNum=1&pageSize=20'

# 人工重投：复位为待投递，由中继按原退避节奏重新投递
curl -s -X POST -H "X-Internal-Token: $LP_INTERNAL_TOKEN" \
  http://localhost:8103/internal/v1/entitlement-outbox/1/retry
```

- 指标名：`entitlement_outbox_pending`、`entitlement_outbox_failed`；刷新间隔由 `lp.trade.outbox-metrics-delay-ms`（默认 15000）控制。
- 建议告警：`entitlement_outbox_failed > 0` 持续 5 分钟即处理，说明已放弃投递、需要人工重投或对账。
- 重投只对 `status=2`（放弃）记录生效，记录不存在或非放弃态返回 `40401`；接口只在内网可达，不经网关转发。

### 4.4 网页购买 ticket（小程序 → 浏览器）

- 依赖：Redis（`LP_REDIS_HOST`/`LP_REDIS_PORT`，默认 `127.0.0.1:6379`）与 auth-service 的内部会话接口。
- 配置：`LP_WEB_BASE_URL`（默认 `http://127.0.0.1:5173`，用于拼接 `https://<web-host>/purchase?ticket=...`）、`LP_AUTH_SERVICE_BASE_URL`（默认 `http://127.0.0.1:8101`）。
- 签发要求登录态；兑换接口允许匿名（浏览器首次打开时没有会话），但若浏览器已登录为其他用户，会返回 `62015` 且不消费 ticket。
- Redis 键与 TTL：`lp:trade:purchase-ticket:{sha256(ticket)}` 15 分钟；`...:issued:{sha256}` 与 `...:used:{sha256}` 各 24 小时，仅用于区分过期/已用/不存在。

```bash
# 1) 小程序侧签发（需 accessToken）
curl -s -X POST http://localhost:8080/api/v1/orders/purchase-ticket \
  -H "Authorization: Bearer $ACCESS_TOKEN" -H 'Content-Type: application/json' \
  -d '{"courseId":1}'

# 2) 浏览器打开返回的 url，或直接兑换拿 token 对
curl -s -X POST http://localhost:8080/api/v1/orders/purchase-ticket/redeem \
  -H 'Content-Type: application/json' -d '{"ticket":"<ticket>"}'
```

- 兑换成功的响应含 `accessToken/refreshToken/expiresIn/courseId`，浏览器据此建立会话后继续 `POST /api/v1/orders`。
- 错误码：`62012` 过期、`62013` 已使用、`62014` 不存在、`62015` 归属其他用户；ticket 不会写入日志。

### 4.5 运营课程审核（admin-service）

- 依赖：course-service（8102）与 `LP_INTERNAL_TOKEN`；admin-service（8106）启动时若 token 为空会直接失败。
- 配置：`LP_COURSE_SERVICE_BASE_URL`（默认 `http://127.0.0.1:8102`）、`LP_COURSE_READ_TIMEOUT_MS`（默认 3000）。
- 权限：网关注入的 `X-User-Roles` 必须含 `ADMIN` 或 `OPS`，否则 `40301`。

```bash
export LP_INTERNAL_TOKEN="local-internal-token"
./mvnw -q -pl lp-admin-service spring-boot:run

# 审核队列（status=1，按提交时间正序）
curl -s 'http://localhost:8080/api/v1/admin/courses/pending?pageNum=1&pageSize=20' \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN"

# 通过并定价（priceFen=9900 表示 99.00 元）
curl -s -X POST http://localhost:8080/api/v1/admin/courses/1/review \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" -H 'Content-Type: application/json' \
  -d '{"approved":true,"priceFen":9900}'

# 驳回必须带 remark
curl -s -X POST http://localhost:8080/api/v1/admin/courses/1/review \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" -H 'Content-Type: application/json' \
  -d '{"approved":false,"remark":"missing outline"}'
```

- 审核决策由 course-service 执行：通过为 `1→2` 并同写价格，驳回为 `1→0` 并记录 `review_remark`；课程不在待审核状态返回 `61005`。
- 内部调用路径为 `/internal/v1/courses/pending` 与 `/internal/v1/courses/{courseId}/review`，均需 `X-Internal-Token`，不经网关。

### 4.6 演示种子数据（仅本地演示）

`flyway migrate` 之后执行一次即可得到可端到端演示的数据：3 个分类、3 位讲师、10 门课程（55 章节 / 189 课时，其中 2 门已下架）、3 个账号、2 笔已支付订单与对应权益和进度。

```bash
bash scripts/flyway.sh migrate all
bash scripts/seed/seed-demo.sh
```

- 连接参数沿用 `scripts/flyway.sh` 的默认值（`lp_app` / `lp_app_dev`，端口取 `infra/.env` 的 `LP_MYSQL_PORT`）；需要覆盖时用 `LP_SEED_HOST`、`LP_SEED_USER`、`LP_SEED_PASSWORD`。
- 脚本幂等：每条业务数据都按自然键做 `NOT EXISTS` 或 `ON DUPLICATE KEY UPDATE` 守卫，重复执行不新增、不删除行；本地实测连续执行两次计数一致。
- 账号（**仅本地演示，禁止用于任何共享环境**）：

| 账号 | 角色 | 说明 |
|---|---|---|
| `demo_admin` | ADMIN | 平台管理员 |
| `demo_buyer` | STUDENT | 已购课程 1 与课程 7，有学习进度 |
| `demo_guest` | STUDENT | 未购买任何课程 |

密码统一为 `Demo@123456`（BCrypt 哈希写在 `db/seed/demo-seed.sql`）。种子只插入缺失数据，不会重置既有账号密码；需要恢复账号状态时先在本地清库再重新 `migrate` + seed。

### 4.7 直连下游服务（仅本机）

`infra/docker-compose.yml` 中 8101–8107 统一绑定为 `127.0.0.1:端口`，容器内网仍可被网关访问，但宿主机之外（同一局域网的其他机器、公网）无法连入；对外入口只有网关 8080。

业务服务已注册 `TrustedIdentityFilter`：除登录、课程目录、支付回调、购课 ticket 兑换与 `/internal/**` 外，缺少 `X-User-Id` 的请求在进入控制器前即返回 `40101`。该过滤器只校验身份头是否存在，不校验调用方来源，因此 8101–8107 严禁改为对宿主机全接口发布；本地直连调试时需要手动补头：

```bash
# 受保护接口：本地直连需显式补 X-User-Id（网关会注入，浏览器无需关心）
curl -s http://127.0.0.1:8103/api/v1/orders -H 'X-User-Id: 2'

# 内部接口：带 X-Internal-Token，值取 infra/.env 的 LP_INTERNAL_TOKEN
curl -s http://127.0.0.1:8107/actuator/health
curl -s http://127.0.0.1:8103/internal/v1/entitlement-outbox/stats \
  -H "X-Internal-Token: ${LP_INTERNAL_TOKEN:-lp_internal_dev_token}"
```

- 课程公开目录（`/api/v1/courses/**`、`/api/v1/categories/**`）与支付渠道回调（`/api/v1/payments/alipay/notify`、`/api/v1/payments/mock/callback`）、匿名 ticket 兑换（`/api/v1/orders/purchase-ticket/redeem`）已在白名单内，不需要身份头；回调验签仍在服务内完成。
- 白名单可通过 `LP_IDENTITY_WHITELIST`（逗号分隔 Ant 路径）按服务覆盖，修改后需重启对应服务。

### 4.8 账号注销执行（本地）

账号注销默认只记录申请、不执行；冷静期到期后的自动执行由 auth-service 定时任务完成，默认关闭，需要显式打开才能本地验证。

| 环境变量 | 默认 | 说明 |
|---|---|---|
| `LP_AUTH_ACCOUNT_DELETION_EXECUTION_ENABLED` | `false` | 打开执行任务；staging / prod 显式设为 `true` |
| `LP_ANONYMIZE_SALT` | 空 | 执行开关打开时必填，为空则启动失败（匿名摘要的盐） |
| `LP_AUTH_ACCOUNT_DELETION_SCAN_DELAY_MS` | `60000` | 相邻两次扫描的间隔 |
| `LP_AUTH_ACCOUNT_DELETION_SCAN_INITIAL_DELAY_MS` | `30000` | 启动后首次扫描的延迟 |

- 旧名 `LP_AUTH_CANCELLATION_EXECUTION_ENABLED` 已于 2026-11-10 改名废弃，**不再生效**；外部脚本若仍用旧名，执行任务会静默保持关闭。
- 执行依赖 trade-service 的 `/internal/v1/account-deletion/anonymize`；trade 不可达时本地匿名化整体回滚，申请单留在 `status=0`，下一轮重试。
- 注销生效后**不主动吊销 refresh token**：Redis 中该账号剩余的 `lp:auth:refresh:*` 键按 TTL 自然过期，最长 7 天（`lp.auth.jwt.refresh-token-ttl-seconds` 默认 `604800`），不要据此认为已即时吊销。真正拦住刷新的是 `status=3` 的状态判定，`/auth/refresh` 与 `/auth/me` 在注销后立即返回 `67001`（AC-14）。
- 执行类指标与告警规则见 [infra/observability/README.md](../infra/observability/README.md)。

### 4.9 本地监控栈（Prometheus / Alertmanager / Grafana）

监控栈独立于主栈，但复用主栈的 `lp-dev_default` 网络，因此按「先应用、后监控」的顺序启动：

```bash
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/observability/docker-compose.yml up -d

curl -s http://127.0.0.1:9090/-/healthy      # Prometheus
curl -s http://127.0.0.1:9093/-/healthy      # Alertmanager
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:3000/login   # Grafana
```

| 组件 | 地址 | 本地边界 |
|---|---|---|
| Prometheus | `http://127.0.0.1:9090` | 抓 8 个服务 + 自身；`rule_files` 卷挂载现有 `prometheus-rules.yml`，不复制规则 |
| Alertmanager | `http://127.0.0.1:9093` | 只有空 receiver，仅验证规则能送达，不发通知 |
| Grafana | `http://127.0.0.1:3000` | 只配 Prometheus 数据源，不铺面板；账号 `admin`，密码取 `LP_GRAFANA_ADMIN_PASSWORD`，默认 `lp_grafana_dev` |

接入与指标口径见 [infra/observability/README.md](../infra/observability/README.md)。两处快速自检：

```bash
# 8 个服务 + prometheus 自身都应 up
curl -s http://127.0.0.1:9090/api/v1/targets | \
  python3 -c "import json,sys;a=json.load(sys.stdin)['data']['activeTargets'];print(len(a),'targets',sum(1 for t in a if t['health']=='up'),'up')"

# 规则组 lp-auth-account-cancellation 的三条规则 health=ok
curl -s 'http://127.0.0.1:9090/api/v1/rules?type=alert' | \
  python3 -c "import json,sys
for g in json.load(sys.stdin)['data']['groups']:
    for r in g['rules']: print(g['name'], r['name'], r['health'], r['lastEvaluation'])"
```

- 主栈重建（`docker compose up -d --build`）不会影响监控栈；反过来停监控栈也不影响业务服务。
- 服务没暴露 registry 时 `/actuator/prometheus` 返回 HTTP 200 但 body 是兜底错误 `{"code":50001,...}`，标签页里看不出异常，必须看 body 才判得准。

### 4.10 AC-33 缓存失效告警演练

`scripts/observability-ac33-drill.sh` 用真实请求跑完整条链路，约 8 分钟，全程**不停止 trade**，只用 `docker pause` 冻结（目标与端口都留在原地，不需要重启任何服务）：

1. 注册 3 个账号，在 trade 仍可达时逐个提交注销申请（申请会探测 trade，trade 不可达时按 AC-10 直接 `50401`，所以申请必须放在暂停之前）；
2. 冻结 trade，撤销这 3 个账号（撤销不做探测，提交后通知 trade 清缓存会失败），等计数器 +3 后解冻；
3. 等告警 firing（`for: 1m`，约 1 分钟内触发），再等它回到 inactive。

冻结只持续到 3 次失效全部超时（约数秒），trade 不会长时间不可用：本地同一套栈还有其他检查在用 trade。**不能撤销完立刻解冻**——排队中的异步失效会在容器醒来后成功，计数器只记到 1 次而不是 3 次。

```bash
bash scripts/observability-ac33-drill.sh
```

脚本自证四样，缺一不算通过：

| 证据 | 断言 |
|---|---|
| 规则在求值 | `/api/v1/rules` 中 `LpAuthAccountCancellationCacheInvalidateFailed` 的 `health=ok`，且相隔 20 秒的两次 `lastEvaluation` 不同 |
| 计数器 | `lp_auth_account_cancellation_cache_invalidate_failed_total` 相对基线增加 3 |
| 两处都能查到 firing | `/api/v1/alerts` 与 `:9093/api/v2/alerts` 都有该告警处于 active；`value` 只从 Prometheus 取，要求 ≥ 3（Alertmanager 不含数值，脚本原样打印响应，不伪造） |
| 窗口过后回 inactive | 这批失败离开 5m 窗口后，`/api/v1/alerts` 不再返回该告警 |

- 告警的 `value` 是 `increase(...[5m])` 在触发那次的取值，**不是**计数器累计值，所以显示约 3；要看累计值查 `/actuator/prometheus`。
- 规则的 `for` 是 **1m**，不是 5m：门槛 `>= 3` 与窗口 `[5m]` 都不动。原来的 `for: 5m` 对单批失败永远不 firing——t=0 写入 3 次事件，条件最多维持到 t=5m，而 `for: 5m` 恰好在 t=5m 才满足持续要求，此刻最早的事件已滑出窗口，pending 必然重置。`for: 1m` 保留「持续时间」意图（过滤瞬时抖动），又能在窗口内触发。
- 演练会留下 3 个处于冷静期的账号（`ac*`），7 天后到期；不需要时可以手工清掉，不影响其他数据。
- 失败时脚本会打印它当时读到的原始 JSON 行；先看 `docker logs lp-auth-service | grep cache` 与 `docker logs lp-trade-service`，再核对 `prometheus-rules.yml` 是否真的挂载进来了。

## 5. Web 前端

```bash
pnpm install
pnpm -C apps/web dev
```

打开 `http://localhost:5173`。开发服务器把 `/api` 与 `/actuator` 代理到 `http://localhost:8080`，因此浏览器始终同源，无需配置 CORS。

提交前校验：

```bash
pnpm -C apps/web lint
pnpm -C apps/web typecheck
pnpm -C apps/web test
pnpm -C apps/web build
```

构建产物写入 `apps/web/dist/`，已被根目录 `.gitignore` 忽略。

### 5.1 运营与教师后台

```bash
pnpm -C apps/admin dev
```

打开 `http://localhost:5174`，代理配置与 Web 相同（`/api`、`/actuator` 转发到 `http://localhost:8080`）。

```bash
pnpm -C apps/admin lint
pnpm -C apps/admin typecheck
pnpm -C apps/admin test
pnpm -C apps/admin build
```

### 5.2 共享包 `packages/shared`

`@lp/shared` 通过 pnpm workspace 依赖（`workspace:*`）被 `apps/web` 与 `apps/admin` 引用；`exports` 直接指向 `src/index.ts`，由 Vue 与 vue-tsc 直接消费，没有独立构建产物。

```bash
pnpm -C packages/shared lint
pnpm -C packages/shared typecheck
pnpm -C packages/shared test
```

## 6. 微信小程序

1. 用微信开发者工具"导入项目"，目录选择 `apps/miniapp`。
2. AppID 选择"测试号"或使用 `project.config.json` 中的 `touristappid` 游客模式。
3. 本地联调时把 `apps/miniapp/app.js` 的 `apiBaseUrl` 指向本机网关，并在开发者工具中关闭"校验合法域名"。

静态校验（CI 同款）：

```bash
pnpm -C apps/miniapp check
node apps/miniapp/scripts/check.mjs
```

校验内容：`project.config.json`、`app.json`、`sitemap.json` 可解析；`app.json` 声明的每个页面都存在 `js / json / wxml / wxss` 四件套。

限制与如实说明：

- `miniprogram-ci` 的编译与上传需要真实 AppID 与上传私钥，私钥禁止入库，因此 CI 不执行 `miniprogram-ci`，只跑上面的静态校验。
- 真机预览、体验版上传与审核发布必须由人工在微信开发者工具和微信后台完成（`AGENTS.md` 第四节已列为需授权操作）。

## 7. 本地校验清单

| 范围 | 命令 | 备注 |
|---|---|---|
| 文档 | `bash scripts/check-docs.sh` | 文件名、相对链接、HTML 标签、旧命名 |
| 提交信息 | `bash scripts/check-commit-msg.sh /tmp/msg` | 英文祈使句、全小写、不超过 60 字符 |
| 后端 | `./mvnw -B test` | 不含打包 |
| 后端格式 | `./mvnw spotless:check` | 只校验自 `origin/develop` 起改动过的文件（ratchet），修复用 `spotless:apply` |
| Web | `pnpm -C apps/web lint` 与 `typecheck` 与 `test` 与 `build` | 与 CI 的 `web-build` 一致 |
| 运营后台 | `pnpm -C apps/admin lint` 与 `typecheck` 与 `test` 与 `build` | 与 Web 同口径 |
| 共享包 | `pnpm -C packages/shared lint` 与 `typecheck` 与 `test` | `@lp/shared` 类型与常量 |
| 本地依赖 | `bash scripts/smoke/run-all.sh` | MySQL / Redis / RocketMQ / MinIO / Nacos 逐项通过或失败 |
| 迁移 | `bash scripts/flyway.sh info all`（或 `migrate all`） | 七库封装，目标库或迁移目录名均可 |
| 钩子 | `bash scripts/install-hooks.sh` | 设置 `core.hooksPath=.githooks`；启用后 `pre-commit` 含 Java 格式门禁 |
| 小程序 | `node apps/miniapp/scripts/check.mjs` | 与 CI 的 `miniapp-check` 一致 |

## 8. 常见问题

- `docker: command not found`：安装 Docker Desktop 并启动；未安装时第 3 节跳过，后端仍可单独构建。
- `ERR_PNPM_IGNORED_BUILDS`：执行 `pnpm approve-builds --all` 放行 `esbuild` 的构建脚本，再 `pnpm install`。
- `git fetch` 提示权限失败 / `Connection closed by 127.0.0.1 port 7897`：见 §0「Git 远端与 SSH 配置」，最终方案是 `ssh.github.com:443` 的 SSH over HTTPS，不需要切 HTTPS remote。
- 前端启动后接口 502：确认网关已在 8080 启动，或 `application.yml` 指向的下游服务未启动（预期）。
- 小程序编译报域名不合法：开发者工具中关闭"校验合法域名"，仅限本地调试。

## 9. 当前实现状态与尚未落地的部分

七个域服务**均已提供业务接口**，不再是"仅健康检查、无业务接口"的 M0 骨架。下表按服务列出 `develop` 已实现端点范围与对应规格条目，未列出的路径一律返回 404。

| 服务 | 端口 | 已实现端点范围 | 对应规格条目 |
|---|---|---|---|
| `lp-auth-service` | 8101 | `/api/v1/auth`（注册、登录、刷新、退出、`me`、小程序 code 登录）；`/internal/v1/auth/session`；`/internal/v1/users`（列表、统计） | SPEC-PLAT-001、SPEC-PLAT-003、SPEC-PLAT-005 |
| `lp-course-service` | 8102 | `/api/v1/categories`；`/api/v1/courses`（列表、详情、章节）；`/api/v1/teacher/courses`（增删改查、`submit`、章节与课时增删改）；`/internal/v1/courses`（详情、课时、`pending`、统计、审核、审核记录）；`/internal/v1/lessons/{lessonId}` | SPEC-COURSE-001、002、003、004、007、008、010、011 |
| `lp-trade-service` | 8103 | `/api/v1/cart`；`/api/v1/orders`（创建、列表、详情、取消、退款申请）；`/api/v1/orders/purchase-ticket`（签发、兑换）；`/api/v1/payments/{orderNo}/pay`、`/api/v1/payments/alipay/notify`、`/api/v1/payments/mock/callback`；`/api/v1/refunds/{refundId}`；`/internal/v1/orders`（列表、统计、指标）；`/internal/v1/refunds`（列表、审核）；`/internal/v1/entitlement-outbox`（统计、失败列表、重投） | SPEC-TRADE-001、002、004、005、006、008、009、010、011、012 |
| `lp-learn-service` | 8104 | `/api/v1/learning/my-courses`；`/api/v1/learning/progress`（上报、查询）；`/api/v1/lessons/{lessonId}/play-token`；`/internal/v1/entitlements`（发放、撤销、已拥有）；`/internal/v1/learning/progress-summary` | SPEC-LEARN-001、002、003、004、005、006、007、008 |
| `lp-ugc-service` | 8105 | **未实现**（无 controller）：评论、收藏、举报均为「上线后首批」 | 当前无对应规格条目，需先补规格再开工 |
| `lp-admin-service` | 8106 | `/api/v1/admin/courses`（`pending`、审核、审核记录）；`/api/v1/admin/dashboard`；`/api/v1/admin/menus`；`/api/v1/admin/metrics/overview`；`/api/v1/admin/orders`；`/api/v1/admin/refunds`（列表、审核）；`/api/v1/admin/users` | SPEC-COURSE-004、SPEC-TRADE-009、SPEC-PLAT-008 |
| `lp-ai-service` | 8107 | **未实现**（无 controller）：AI 能力为「上线后首批」 | SPEC-PLAT-007（AI 模型接入 BYO） |

七个服务中仍在接口层之外的未落地项：

- 真实支付宝通道未接，当前仅 `LP_PAY_MOCK_ENABLED=true` 的模拟支付；对账能力见 SPEC-TRADE-014（真实收款前置）。
- 教师入驻审核（SPEC-COURSE-009）、教师下架与再上架（SPEC-COURSE-005）、运营强制下架（SPEC-COURSE-006）无接口实现。
- `apps/web`、`apps/admin`、`apps/miniapp`、`packages/shared` 可安装、可校验、可构建；教师建课目前只有后端接口，没有后台界面。

其余基建状态：

- `scripts/install-hooks.sh`、`scripts/flyway.sh`、`scripts/smoke/{mysql,redis,rocketmq,minio,nacos}.sh` 与 `scripts/smoke/run-all.sh` 已创建并实测通过。
- `db/migration/ops/V1__baseline.sql` 归属独立库 `lp_ops`（工程规划 15.2 D13）：`admin-service` 不再与 `lp_ums`/`lp_cms` 共享 schema history，`scripts/flyway.sh` 用 `ops` 或 `lp_ops` 目标执行，`admin` 目标按未知目标报错。
- `infra/up.sh`、`infra/down.sh`、`infra/reset.sh` 与 `scripts/gen-service.sh`、`scripts/preflight.sh`、`scripts/check-migration.sh` 仍为 M0 规划项（见工程规划 13.6），未创建前以上述 compose 与已创建脚本为准。
- MinIO 使用归档镜像 `bitnamilegacy/minio`，M1 前需评估 SeaweedFS、Garage 或云对象存储替代方案。
- `docs/local-dev-guide.md`（本文件）随功能切片持续更新，新增脚本或接口时同步修改对应章节。
