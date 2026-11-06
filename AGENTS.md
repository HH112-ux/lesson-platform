# AGENTS.md — 课程平台仓库级工作规则（人与 AI Agent 通用）

> **这是什么**：本仓库（monorepo：后端服务 + `apps/web` + `apps/admin` + `apps/miniapp`）的**仓库级工作规则**。Agent 启动即自动加载 AGENTS.md，把它作为开工前必读的第一份约束；它不是说明文档，而是**可判定、可执行的条款**。
> **谁必须遵守**：人类工程师与所有 AI Agent **同等适用、无豁免**。Agent 不得以"提高效率""时间紧""只是小改动"为由放宽任何条款。
> **生效范围**：整个仓库，含后端服务（`gateway/auth/course/trade/learn/ugc/admin/ai`）、Web 端（`apps/web`）、运营与教师后台（`apps/admin`）、微信小程序（`apps/miniapp`）、数据库迁移（Flyway）、CI/CD 与文档。
> **与项目文档的关系**：`docs/engineering-plan.md` 描述"做什么、怎么设计"；AGENTS.md 规定"怎么做、什么禁止"。该文档第 9 章只写工程流程差异并引用 AGENTS.md，**执行规则以 AGENTS.md 为准**。
> **开工前要求**：任何写操作（改文件、切分支、commit、push）之前，**必须先完整读完 AGENTS.md**，再执行第二节的开工四查；未读完不得动手。
> **冲突处理**：AGENTS.md 与用户当轮指令、项目文档、CI 配置冲突时，**立即停止操作**，写明冲突点（规则条款 / 涉及文件 / 两种做法），交负责人裁决；禁止自行取舍，禁止"先做了再说"。
> **文档边界**：本仓库常驻规则/方案文件为 AGENTS.md 与 `docs/` 下的 `engineering-plan.md`、`product-lifecycle.md`（`local-dev-guide.md` 为 M0 规划产出），不新建同类文档。

---

## 一、适用范围与优先级

**适用范围**：本仓库内一切 Git 与协作操作，包括但不限于 commit、branch、push、fetch、pull、rebase、merge、tag、revert、reset、PR 创建与合并，以及 CI/CD 流水线配置、数据库迁移脚本与文档的改动。

**优先级（高 → 低）**：

1. 用户/负责人在**当前对话轮次或当前工单**中给出的明确指令；
2. AGENTS.md；
3. 仓库内其他约定（`.github/workflows/*`、`docs/engineering-plan.md` 第 9 章、`commitlint` 配置等）。

**冲突处理**：任意两层冲突时，**停止操作**，把冲突点写清楚（哪一条规则、哪一个文件、两种做法），交负责人裁决。禁止自行取舍。

**项目硬约束（优先于任何"效率"诉求）**：

- 单仓多端，但**发布不是一次**：后端 + Web 先发，微信小程序后发（至少错开 7 天，见方案 12.1）。
- 三套环境：`dev`（本地/联调）、`staging`（`develop` 自动部署）、`prod`（`main` 打 tag 后人工发布）。
- **支付（支付宝 / 微信支付）与鉴权（网关身份头、JWT、教师归属校验）属于高风险改动区**，适用更严格的评审与验证要求（见第八节）。

---

## 二、开工前检查与开工条件

任何写操作（改文件、切分支、commit）之前，必须依次执行并**读取输出**：

```bash
git status
git branch --show-current
git log --oneline -5
git remote -v
```

| 检查结果 | 必须动作 |
|---|---|
| 当前在 `main` / `develop` | 先建分支再改代码，禁止"先改后搬"：`git fetch origin && git switch -c feature/xxx origin/develop` |
| 工作区有未提交改动 | **保留，不覆盖、不丢弃**；最终报告中列出文件清单并说明"用户改动已保留" |
| 存在并行 Agent / 多会话同仓 | 使用独立分支，必要时 `git worktree add ../lp-feat-xxx feature/xxx`，并在报告开头声明分支名 |
| 远端有新提交 | 先 `git fetch origin` 处理分叉，再提交 |
| 全部干净 | 按任务执行 |

**禁止**：在未读 `git status` 的情况直接 `git add`；在不明确当前分支的情况下提交。

---

## 三、绝对禁止（红线）与违规处理

以下行为在本仓库**无条件禁止**，唯一例外是负责人在当轮明确点名该具体命令：

| 编号 | 禁止行为 | 后果 |
|---|---|---|
| F1 | `git push --force` / `--force-with-lease` 到 `main`、`develop` 及任何他人参与的分支 | 重写共享历史，需人工紧急修复 |
| F2 | 在 `main` / `develop` 上直接 commit 或 push | 绕过评审，破坏受保护分支 |
| F3 | `git reset --hard`、`git clean -fdx`、`git checkout -- .`、`git restore .` | 丢弃未提交改动，不可恢复 |
| F4 | `git commit --no-verify` / `-n`，或临时禁用 husky、gitleaks、commitlint | 绕过质量与密钥门禁 |
| F5 | 对**已推送**提交执行 `rebase` / `amend` | 改写公共历史，同 F1 |
| F6 | 提交任何密钥、证书、`.env`、IDE 目录（`.idea`、`.vscode`）、大于 5MB 的二进制 | 凭据泄露 / 仓库膨胀 |
| F7 | `git add -A`、`git add .`、`git add -u` 笼统暂存；把无关改动混进同一 commit | 提交不可审查、不可回滚 |
| F8 | 删除他人分支、删除已发布的 tag（`v*` / `mp-v*`） | 破坏他人工作与版本可追溯性 |
| F9 | 未实际运行验证就声称"已验证 / 测试通过" | 误导负责人，引入未验证缺陷 |
| F10 | Agent 自行合并 PR、自行把 draft 转 ready | 绕过人工评审 |
| F11 | 在小程序分支里顺带改后端契约或价格/支付字段（`price`、`channel`） | 契约漂移，小程序版本不可回退 |

**3.2 敏感信息与密钥（F6 的展开）**

- 禁止入库：JWT RS256 私钥、支付宝应用私钥与公钥证书、微信支付 APIv3 密钥与平台证书、微信 AppSecret、数据库口令、MinIO AK/SK、CDN/短信供应商密钥。
- 允许入库：占位符与示例值（`${ALIPAY_APP_PRIVATE_KEY}`）；密钥只经 K8s Secret 与 Nacos 加密配置下发。
- 测试数据禁止使用真实手机号、身份证、`openid`/`unionid`、真实订单号；一律用 `[PHONE_1]`、`[ORDER_NO_1]` 等占位符。
- 发现疑似密钥（`-----BEGIN PRIVATE KEY-----`、`sk-...`、`password = "..."`）：**立即停止**该文件的编辑与暂存，报告文件路径与行号范围与密钥**类型**，不回显明文，等负责人处理。

**3.3 违规处理**

1. 已推送到共享分支的违规提交：值班 owner 执行 `git revert` 并在 PR 中留痕；禁止 reset 修复。
2. 疑似密钥已推送：视为安全事件，立即轮换密钥 + 记入事故记录，不论是否已 revert。
3. AI Agent 同一任务内出现两次红线违规：终止该 Agent 任务，改由人类执行，并在任务记录中标注。

---

## 四、需授权操作与例外审批路径

**默认不执行**，必须获得当轮明确同意：

| 操作 | 授权要求 | 授权后命令 |
|---|---|---|
| `git push` 到远端 | 每次 push 单独授权，不跨轮次、不跨分支 | `git push -u origin feature/xxx` |
| 创建 PR | 默认建 draft，创建动作本身仍需确认 | `gh pr create --draft` |
| 打 tag 并推送 | 必须有负责人签字的发布单 | `git tag -a v1.2.3 -m "..." && git push origin v1.2.3` |
| 删除分支（本地/远端） | 逐次授权 | `git branch -d feature/xxx` |
| 改 CI/CD（`.github/workflows/*`） | 说明影响面后授权 | — |
| 改 Flyway 迁移脚本 | 说明 schema 影响与回滚方案后授权 | — |
| 升级/新增依赖 | 说明兼容性与体积影响后授权 | — |
| 小程序提交审核 / 发布 | **必须由人类在微信后台操作**，Agent 只允许上传体验版 | `miniprogram-ci upload` |

**4.2 例外审批路径**

- 生产热修（P0/P1）可跳过 `develop`，流程：从 `main` 切 `hotfix/<工单号>` → 双人评审 → 合 `main` 打 `vX.Y.(Z+1)` → **24 小时内回合 `develop`**。例外必须写在 PR 正文首行：`[HOTFIX] 原因 + 影响面 + 回滚方式`。
- 任何"这次先不加测试/先不跑全量"的例外，必须由模块 owner 在 PR 中书面确认，不允许口头或聊天记录替代。
- 例外不适用于红线（第三节），红线无审批路径。

---

## 五、分支模型与命名

**分支模型**（三环境对应）：

| 分支 | 用途 | 保护策略 | 部署目标 | 来源 |
|---|---|---|---|---|
| `main` | 生产基线 | 保护：必须经 PR 且 `docs-check` 通过；禁强推、禁删除；管理员同样受约束 | `prod`（打 tag 后人工发布） | 只接受 PR |
| `develop` | 集成 | 保护：必须经 PR 且 `docs-check` 通过；禁强推、禁删除；管理员同样受约束 | `staging`（自动） | 只接受 PR |
| `feature/*` | 功能 | 无保护，仅本人推送 | 无 | `origin/develop` |
| `fix/*` | 缺陷（非线上） | 无保护 | 无 | `origin/develop` |
| `hotfix/*` | 线上紧急修复 | 无保护 | 先 `prod` 后回合 `develop` | `origin/main` |
| `chore/*` | 配置、依赖、文档、CI | 无保护 | 无 | `origin/develop` |

**命名规则**：

- 格式：`<type>/<scope>-<短语>`，全小写，短横线分隔，**不得含中文、空格、下划线**。
- `scope` 必须取自本仓库模块：`gateway`、`auth`、`course`、`trade`、`learn`、`ugc`、`admin`、`ai`、`web`、`admin-web`、`miniapp`、`db`、`ci`。
- 短语 2–4 个英文单词，如 `feature/trade-wxpay-callback`、`fix/miniapp-login-code`、`chore/ci-miniprogram-upload`。
- 小程序专属改动统一用 `miniapp` scope（如 `feature/miniapp-pay-jsapi`），便于按路径过滤 CI 与评审人。
- 续做他人/上一轮分支：沿用原分支，禁止重命名；`git fetch origin && git switch feature/xxx && git pull --ff-only`。
- 分支存活 ≤ 5 个工作日；超过则拆分并在报告中给出合并顺序。

**Rebase 边界**：仅允许对**本地未推送**提交 rebase；自己独占、未合并的 feature 分支 rebase 后可用 `--force-with-lease` 推**自己的分支**；`main`/`develop`/他人分支禁止。

---

## 六、提交信息与提交粒度

**格式**：

```text
<type>(<scope>): <subject>

[body]

[footer]
```

- `type` 白名单（不得自创）：`feat`、`fix`、`docs`、`style`、`refactor`、`perf`、`test`、`chore`、`revert`。CI 与构建统一用 `chore`。
- `scope`：见第五节的模块清单；无法归类时省略，写作 `feat: xxx`。
- `subject`：**英文**、祈使句动词原形、全小写、≤ 60 字符、结尾不加句号、不得与 scope 重复、不得重复 type 动词。
- `body`：写"为什么"而不是"改了什么"，每行 ≤ 72 字符。
- `footer`：破坏性变更写 `BREAKING CHANGE: <说明>`，header 的 type 后加 `!`；关联工单写 `Closes #123`（必须落在 footer）。

正例：

```text
feat(trade): add wxpay callback verify and decrypt
fix(auth): fix miniapp login code replay check
chore(deps): upgrade wechatpay-java to 0.2.12
```

**粒度要求**：

1. 一 commit 一事：格式化、依赖升级、业务逻辑、迁移脚本**不得混在同一 commit**。
2. 暂存必须按文件名精确 `git add <file>`。
3. 支付通道改动（`trade`/`pay`）与鉴权改动（`gateway`/`auth`）必须独立 commit，便于单独 revert。
4. Flyway 脚本单独提交，commit body 必须写 `migrate: V{version} <表名>` 与回滚方式。
5. `apps/miniapp` 与后端契约改动不得同 commit（便于小程序单独回退）。

---

## 七、提交前验证与门禁

| 改动范围 | 最低验证 | 失败处理 |
|---|---|---|
| 后端 Java 逻辑 | `mvn -q compile` + 相关模块单测 | 失败不得提交 |
| 后端格式 | `mvn spotless:check` | `spotless:apply` 后重检 |
| `apps/web` / `apps/admin` | `pnpm lint && pnpm typecheck && pnpm test` | 失败不得提交 |
| `apps/miniapp` | `pnpm lint` + 开发者工具真机预览（登录、下单、支付、播放各一次） | 失败不得提交 |
| 支付 / 鉴权相关 | 上述 + 对应单测与回调验签用例（含伪造回调负例） | 失败不得提交 |
| Flyway 迁移 | `mvn flyway:validate` | 失败不得提交 |
| 文档 / 配置 | `bash scripts/check-docs.sh`（链接、HTML、文件名、旧命名）；无校验则跳过并在报告说明 | 失败不得提交 |

- 钩子（husky / gitleaks / commitlint / `.githooks`）拦截时必须读输出、修根因、重新提交；**禁止绕过**。
- 报告中必须区分"已运行并通过""已运行但失败""未运行"，禁止模糊表述。

### 7.1 本地钩子启用与 bootstrap 例外

- **启用（每台机器执行一次）**：`git config core.hooksPath .githooks`（或 `bash scripts/install-hooks.sh`）。启用后 `pre-commit` 始终跑 `scripts/check-docs.sh`，**暂存区含 `*.java` 时**再跑 `timeout 180 ./mvnw -B -q spotless:check`；`commit-msg` 跑 `scripts/check-commit-msg.sh`。除 Maven 外均为纯 shell，不依赖 Node。
- **Java 改动提交前必跑格式校验**：`spotless:check` 由 `pre-commit` 拦截；失败时按提示执行 `./mvnw spotless:apply`，**修完必须重新 `git add`** 再提交。钩子只检查不修改，不会自动格式化或自动暂存。`--no-verify` 属于需要说明理由的例外，不得作为常态。
- **ratchet 基线**：`spotless` 的 `ratchetFrom` 为 `origin/develop`，钩子提交前会 `git fetch --quiet origin develop`；fetch 失败只警告不阻断，但基线可能过期，需自行确认或重试 fetch。
- **验证**：`bash scripts/check-docs.sh && echo "chore: smoke commit" > /tmp/msg && bash scripts/check-commit-msg.sh /tmp/msg`。
- **bootstrap 例外**：仓库初始化阶段允许直接在 `develop` 上提交并推送（此时分支保护尚未开启）；保护开启后一律按第二节从 `origin/develop` 切分支、经 PR 合入，例外自动失效，无需另行审批。
- CI 端同等校验见 `.github/workflows/docs-ci.yml`；本地钩子未启用不得作为跳过 CI 的理由。

### 7.2 分支保护实际口径（GitHub 已启用）

- 适用分支：`main` 与 `develop`。规则：必须经 PR 合入；required status check `docs-check` 必过；`enforce_admins=true`，**管理员同样受约束**；`required_approving_review_count=0`（单人仓库无法自批，合并不等待 approve）；禁止 force push、禁止删除分支；合并后自动删除源分支。
- 合并方式：`gh pr merge --squash`；approve 为 0，不需要也不得使用 `--admin` 绕过评审。若常规合并被拦，说明保护配置与本节不符，**停止并报告负责人**，不得绕过。
- 7.1 的 bootstrap 例外自保护开启起失效；直推 `develop` 须改走 PR 合入。

---

## 八、PR 评审、合并与发布版本

**PR 标题**：与 commit header 同格式（squash 后该标题即进入 `main`）。**标题本身也计入长度约束**：squash 合并时 GitHub 会在标题后追加 ` (#<PR号>)`，因此 PR 标题**建议 ≤ 55 字符**，为后缀留出余量，避免合并后撞 60 字符上限；校验脚本会先剥离该后缀再判定格式与长度。

**PR 正文六段（缺一不可）**：变更说明 / 自测情况（命令与结果）/ 关联工单 / 风险点 / 数据库变更说明（是否含迁移、能否回滚）/ 回滚方案。

**评审与合并**：

| 项 | 规则 |
|---|---|
| 单 PR 变更量 | ≤ 400 行；超出需拆分并说明拆分理由 |
| 常规 PR | CI 全绿（`docs-check` 必过）；approve 数量为 0（单人仓库无法自批，合并不等待 approve） |
| 高风险 PR（支付、鉴权、教师归属、退款、对账） | 单人仓库阶段 approve 为 0；成员加入后升级为 **2 人 approve**（其一为对应模块 owner），PR 需附"越权/验签/金额一致性"用例结果 |
| 合并方式 | 合入 `develop`：squash；`hotfix/*` 合 `main`：merge commit 保留追溯 |
| 合并执行者 | **人类**；Agent 不合并、不转 ready |
| 禁止 | PR 中混入无关重构；限制 CI 覆盖范围来"变绿" |

**发布与 tag 版本对应**：

| 发布物 | 分支 | tag 格式 | 对应关系 |
|---|---|---|---|
| 后端服务 + Web（`apps/web`、`apps/admin`） | `main` | `vX.Y.Z`（附注 tag） | tag 必须指向通过 staging 验收的那个 commit；镜像 tag 与之一致 |
| 线上热修 | `main` | `vX.Y.(Z+1)` | 必须由对应 `hotfix/*` 合并产生 |
| 微信小程序（`apps/miniapp`） | `main` | `mp-vX.Y.Z` | 与后端版本**不必相同**；tag 的 body 必须记录微信后台版本号、审核单号、对应 commit |
| 破坏性接口变更 | `main` | `vX.(Y+1).0`，接口路径升 `/api/v2` | 旧版 `/api/v1` 至少保留 90 天且不得提前下线 |

- tag 只允许从 `main` 打；只打一次，禁止移动或删除已发布 tag。
- 发布顺序：后端 + Web 先发 → 观察 ≥ 7 天 → 小程序发（方案 12.1）。小程序 tag 的 commit 必须是后端已上线的 commit 或其后续兼容提交。
- 版本号语义：修 bug 升 PATCH，加向后兼容功能升 MINOR，破坏性变更升 MAJOR。

---

## 九、回滚、冲突与异常

**回滚**：

| 场景 | 命令 / 动作 |
|---|---|
| 回滚普通提交 | `git revert <hash>`，commit message 为 `revert: <被回滚提交的完整 header>` |
| 回滚合并提交 | `git revert -m 1 <hash>` |
| 本地未推送、保留改动 | `git reset --soft HEAD~1` |
| 线上后端/Web | 镜像回滚（保留最近 10 个 tag）+ 配置回滚；数据库按 expand-contract 处理 |
| 线上小程序 | **不能一键回退**：优先服务端开关降级（`pay.channel.wxpay.enabled`、`mp.enabled`）；必须回退版本时重新提交审核（1–7 天），tag 记录新版本号 |

- 代码回滚 ≠ 数据库回滚：涉及 Flyway 时必须说明 schema 兼容性与数据修复方式，禁止 `DROP TABLE/COLUMN` 式破坏性迁移。
- 小程序相关的任何风险点，必须先在服务端留可关闭的开关，PR 中写明开关名与降级表现。

**冲突与异常**：

| 场景 | 必须动作 |
|---|---|
| merge/rebase 冲突 | 停止，保留双方改动，报告冲突文件、原因、涉及提交，等负责人裁决；禁止 `--ours`/`--theirs` 批量覆盖 |
| push 被拒（non-fast-forward） | 禁止强推；`git fetch origin` 后报告领先/落后提交，由负责人决定 rebase 或 merge |
| CI 失败 | 读日志定位根因；同一失败最多重试 1 次；禁止反复重试 |
| 网络/权限失败 | 如实报告错误，禁止反复重试或声称成功 |
| 不确定是否破坏他人工作 | 一律先问 |

---

## 十、AI Agent 额外约束与交付报告

**额外约束（人类工程师不适用）**：

1. 开工前必须完成第二节的四条检查，并在报告中给出现状（分支、工作区是否干净）。
2. **不得擅自改写与本任务无关的文件**：不顺手改格式、不改依赖版本、不重构无关代码、不删除"看起来无用"的代码或文档。
3. **不得跳过 hooks**；钩子失败必须修根因（含 gitleaks 报出的密钥）。
4. **不得强推**，不得对他人分支做任何写操作。
5. **大改必须先说明再动手**（先给方案、影响文件清单、验证计划，等确认）：涉及 ≥ 5 个文件、跨 ≥ 2 个端（后端/Web/小程序）、任何 Flyway 迁移、任何 CI 配置、任何支付/鉴权/教师归属逻辑、任何 `price`/`channel` 字段语义变更。
6. **不得自行创建 PR、合并 PR、打 tag、提交小程序审核**；这些只能由人类执行。
7. 修改既有文档时保持其结构与既有章节编号，不新建额外文档；本仓库只维护一份工程规划文档。新增命名/目录问题时先按第十一节判定。
8. 交付前必须自检是否违反第三节任一红线，并在报告里明确"未触碰"。

**交付报告六项（缺一不可）**：

1. 当前分支（`git branch --show-current`）。
2. 本次 commit 短 hash + header（如无提交，写"未提交"及原因）。
3. 改动文件清单（`git diff --name-only <base>...HEAD`）。
4. 实际执行的验证命令与结果（逐条 + 通过/失败）。
5. 未做/待确认事项（未跑的全量测试、未推送分支、未创建 PR）。
6. 需要人类执行的动作（转 ready、合并、打 tag、提交小程序审核）。

多 Agent 并行时，报告开头声明所占用分支。

## 十一、命名与目录规范

**总则**：标识符（文件名、目录名、表名、字段名、接口路径、配置项、环境变量、分支、tag、CI job）一律使用 **ASCII 英文**；中文只出现在文档标题、正文与字段说明里。禁止在标识符中使用中文、空格、下划线混用、大写混用或表情符号。

### 11.1 文件与目录

- **必须**：文档命名统一为 `英文短语-kebab-case.md`（无数字前缀），与文档业务主题一致；仅当确实存在有序系列（如数据库迁移脚本、发布步骤）时才使用编号。同一目录内风格统一。本仓库 docs 采用「全英文短名」，文档标题行与正文仍用中文。
- **必须**：根目录常驻文件只允许 `AGENTS.md`；`docs/` 只放文档；源码与构建配置放各自目录，不得在根目录堆临时文件。
- **禁止入库**：`.DS_Store`、`.screenshots/`、`*.tmp`、`*.log`、`node_modules/`、`dist/`、`target/`、`.idea/`、`.vscode/`（`AGENTS.md` 第三节 F6 已列为红线）。
- **禁止**：新增说明类文档前不检索既有文档；本仓库只维护一份工程规划文档，其余内容并入既有章节。

> 反例 1：`docs/新方案 V2 (final).md`（空格、大写与中文混排）。

### 11.2 文档内部

- **必须**：章 `# N. 标题`，节 `## N.M 标题`，子节 `### N.M.K 标题`；编号连续、不跳号。
- **必须**：插入新节时顺延后续编号，并 `grep` 全文修正旧引用（如「见第 8.6 节」）。
- **必须**：术语以项目词表为准——教师（不写「老师」）、学员（购买并学习的人）、用户（账号统称）、章节（课时的上级分组）、课时（单个视频）、订单（`trade_order` 的业务称谓）、权益（不写「授权」表达权益）。
- **必须**：同类表格列结构一致——接口表固定「方法 / 路径 / 用途 / 请求 / 响应 / 主要错误码」，建表固定「字段 / 类型 / 约束 / 说明」。

### 11.3 数据层

- **必须**：库名 `lp_{域}`（`lp_ums / lp_cms / lp_oms / lp_lms / lp_ugc / lp_ai / lp_ops`）；表名 snake_case、**单数**、全小写（`course_lesson`、`trade_order`）。
- **必须**：字段 snake_case；外键 `{单数表名}_id`；时间字段统一 `*_at`（`created_at / updated_at / paid_at`），禁止 `create_time / gmt_create`；布尔用 `is_ / has_` 或 `status` 枚举。
- **必须**：索引 `idx_{列名组合}`、唯一 `uk_{语义}`、全文 `ft_{列名}`，禁止 `index1 / uk1` 等无意义命名。
- **必须**：枚举用 `TINYINT` + 代码常量类，字段说明写成「0 x / 1 y」，禁止字符串魔法值。
- **禁止**：表名用复数、用保留字裸名（`order`、`group`、`key`）；确需表达业务域时加前缀（`trade_order`）。

> 反例 2：`orders`（复数）、`create_time`（与全库 `created_at` 不一致）。

### 11.4 接口层

- **必须**：路径 `/api/v{版本}/{资源复数}`，kebab-case 全小写；资源用复数名词（`/courses`、`/orders`）；嵌套 ≤ 2 层；动作用子路径（`/publish`、`/refund`）。
- **必须**：路径参数用 `{camelCase}`（`{courseId}`）；分页固定 `pageNum / pageSize`（默认 1 / 20，上限 100），排序 `sort`；JSON 字段 camelCase，数据库字段 snake_case，映射集中在 DTO。
- **必须**：HTTP 方法语义——GET 查询、POST 创建或动作、PUT 全量更新、PATCH 局部更新、DELETE 删除；查询禁止用 POST。
- **必须**：错误码分段——`4xxxx` 客户端/协议（400 参数、401 凭证、403 权限、404 资源、409 状态、429 限流）、`5xxxx` 服务端与依赖（500 未捕获、502 第三方渠道、503 AI 链路）、`6xxxx` 业务（第 2 位为域：61 课程、62 交易、63 学习、64 互动、65 AI 会话、66 小程序登录）；同一语义不得跨段重复定义。
- **必须**：请求/响应头用完整英文单词（`X-Auth-Timestamp`、`X-Auth-Signature`），禁止 `Ts / Sign` 之类缩写。

> 反例 3：`/api/v1/course/page`（资源单数 + 动词式路径）。

### 11.5 工程配置

- **必须**：环境变量 `UPPER_SNAKE_CASE` 且带前缀 `LP_`（`LP_DB_URL`）；配置项 Spring kebab-case（`lp.pay.channel.alipay.enabled`）；Nacos dataId `lp-{服务}-{env}.yaml`。
- **必须**：服务名 `{域}-service`（`auth-service`、`trade-service`）；包名 `com.lp.{域}`；Maven 模块名与服务名一致。
- **必须**：分支按第五节格式 `{type}/{scope}-{短语}`；tag 后端/Web 用 `vX.Y.Z`、小程序用 `mp-vX.Y.Z`，一个 tag 只打一次。
- **必须**：CI job 命名 `{端}-{动作}`（`backend-build`、`web-lint`、`miniapp-upload`），禁止 `job1 / step2`。

### 11.6 目录规划与实际状态

- **必须**：文档中出现的目录标注状态——**已存在**或**规划中**；规划中的目录不得写成「已有」。
- **必须**：`apps/web`、`apps/admin`、`apps/miniapp`、后端各 `*-service` 模块在仓库尚未创建前，一律标注「规划中」。
- **禁止**：引用不存在的脚本/目录（如 `ml-generator/SQL/`）而不标注来源与状态。

### 11.7 代码注释与 Javadoc

- **必须**：代码一律英文——类/方法/变量命名、注释、日志、异常消息、Javadoc 与提交信息均使用英文；中文只出现在文档。
- **必须**：所有 `public`/`protected` 的类型（类、接口、枚举）与成员方法必须写 Javadoc。类型级说明职责并附 `@author Caspian` 与 `@since`；方法级说明做什么，逐参数写 `@param`，有返回值写 `@return`，可能抛异常时写触发条件的 `@throws`。
- **必须**：语义不直观的常量与公开字段必须写 Javadoc。
- **禁止**：无信息量注释（如 `// get id`、无负责人与工单的 `// TODO`）；行内注释只解释"为什么"，不复述代码。
- **判定**：新增或修改的 `public`/`protected` 声明，其上一行无 Javadoc 即视为不合规，评审时直接打回。

---

## 速查（一页）

**必做**：开工四查 → 从 `origin/develop` 切分支 → 精确 `git add` → 提交前跑验证 → 交付六项报告。

**必问**：push、建 PR、打 tag、删分支、改 CI、改迁移、升依赖、小程序上传/发布、`reset --hard`。

**禁止**：强推共享分支、在 `main`/`develop` 直接提交、`reset --hard`/`clean -fdx`、`--no-verify`、rebase 已推送提交、提交密钥、`git add -A`、删他人分支/已发布 tag、未验证声称通过、Agent 自行合并 PR。
