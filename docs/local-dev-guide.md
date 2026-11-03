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
apps/admin/            运营与教师后台（规划中）
packages/shared/       Web 与 admin 共用类型与请求封装（规划中）
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

首次启动会由 MySQL 容器自动执行 `infra/mysql/init/00-create-databases.sql`，创建 `lp_ums`、`lp_cms`、`lp_oms`、`lp_lms`、`lp_ugc`、`lp_ai` 六个库与业务账号。

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
| Web | `pnpm -C apps/web lint` 与 `typecheck` 与 `test` 与 `build` | 与 CI 的 `web-build` 一致 |
| 小程序 | `node apps/miniapp/scripts/check.mjs` | 与 CI 的 `miniapp-check` 一致 |

## 8. 常见问题

- `docker: command not found`：安装 Docker Desktop 并启动；未安装时第 3 节跳过，后端仍可单独构建。
- `ERR_PNPM_IGNORED_BUILDS`：执行 `pnpm approve-builds --all` 放行 `esbuild` 的构建脚本，再 `pnpm install`。
- `git fetch` 提示权限失败 / `Connection closed by 127.0.0.1 port 7897`：见 §0「Git 远端与 SSH 配置」，最终方案是 `ssh.github.com:443` 的 SSH over HTTPS，不需要切 HTTPS remote。
- 前端启动后接口 502：确认网关已在 8080 启动，或 `application.yml` 指向的下游服务未启动（预期）。
- 小程序编译报域名不合法：开发者工具中关闭"校验合法域名"，仅限本地调试。

## 9. 尚未落地的部分

- 七个域服务已有 M0 骨架（端口 8101–8107，仅健康检查，无业务接口）；`apps/admin`、`packages/shared` 仍为规划中，目录尚不存在。
- `scripts/install-hooks.sh`、`scripts/flyway.sh`、`scripts/smoke/*` 等 M0 脚本未创建。
- `docs/local-dev-guide.md`（本文件）随 M0 骨架产出，后续新增脚本时同步更新对应命令。
