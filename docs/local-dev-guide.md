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

## 9. 尚未落地的部分

- 七个域服务已有 M0 骨架（端口 8101–8107，仅健康检查，无业务接口）；`apps/web`、`apps/admin`、`apps/miniapp`、`packages/shared` 均为 M0 最小骨架（可安装、可校验、可构建）。
- `scripts/install-hooks.sh`、`scripts/flyway.sh`、`scripts/smoke/{mysql,redis,rocketmq,minio,nacos}.sh` 与 `scripts/smoke/run-all.sh` 已创建并实测通过。
- `db/migration/ops/V1__baseline.sql` 归属独立库 `lp_ops`（工程规划 15.2 D13）：`admin-service` 不再与 `lp_ums`/`lp_cms` 共享 schema history，`scripts/flyway.sh` 用 `ops` 或 `lp_ops` 目标执行，`admin` 目标按未知目标报错。
- `infra/up.sh`、`infra/down.sh`、`infra/reset.sh` 与 `scripts/gen-service.sh`、`scripts/preflight.sh`、`scripts/check-migration.sh` 仍为 M0 规划项（见工程规划 13.6），未创建前以上述 compose 与已创建脚本为准。
- MinIO 使用归档镜像 `bitnamilegacy/minio`，M1 前需评估 SeaweedFS、Garage 或云对象存储替代方案。
- `docs/local-dev-guide.md`（本文件）随 M0 骨架产出，后续新增脚本时同步更新对应命令。
