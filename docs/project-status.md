# 1. 文档说明

## 1.1 用途与权威性

本文件是仓库的项目进度档案，供负责人在开工前判断"现在做到哪、下一步做什么"。结论以**代码与已合并提交**为准，不以文档标题或任务清单的打勾状态推断。产品范围以 [product-lifecycle](./product-lifecycle.md) 为准，工程方案以 [engineering-plan](./engineering-plan.md) 为准；本文件只记录实际进度与差距，不重复方案正文。

本文件与 `AGENTS.md` 第二节配套：开工前除四条 git 检查外，必须读本文件确认当前阶段与未完成项。

## 1.2 维护规则

- 每合并一个影响进度的 PR，更新对应行的"最后核对日期"与完成程度；不允许只改日期不改结论。
- 任一行的"最后核对日期"超过 30 天即视为过期，开工前必须重新核对代码，不得直接把旧结论当作事实。
- 只记录可验证的事实、差距与建议；"建议"不等于已排期任务。

---

# 2. 当前阶段

## 2.1 最新合并状态

| 分支 / 标签 | commit | 日期 | 说明 |
|---|---|---|---|
| `develop` | `7d10235` | 2026-11-13 | PR #294 支付回调 AC-1/AC-7 拆分；同批 #290–#292 退款渠道状态可视（trade 只读接口 → admin 代理 → 管理端展示）；核对日期 2026-11-13 |
| `main` | `7364af7` | 2026-11-09 | PR #97，与开发线尚未对齐，下次发布合并时同步 |
| `v0.1.0` | 2026-11-06 | — | 首个可演示版本标签，指向退款闭环完成后的提交 |

## 2.2 里程碑状态

| 里程碑 | 状态 | 判定依据 | 最后核对日期 |
|---|---|---|---|
| M0 基建 | 完成 | Compose、Flyway、网关、统一返回、身份头、CI、本地验收脚本均可运行；`infra/observability` 已提供 Prometheus / Alertmanager / Grafana 本地编排与抓取配置 | 2026-11-10 |
| M1 账号与内容（含教师端） | 部分完成 | 注册登录、RBAC 种子、分类/课程/章节课时、教师课程 API、课程审核与审计、教师入驻审核接口已通（入驻审核为接口级）；教师建课/改价/提交审核/撤回页面、章节课时管理（新增章节、新增/编辑课时）与课时视频上传页（`/lesson-videos`）已可用，2026-11-11 在浏览器里从零建课经章节、课时、上传确认、编辑到提交审核全链路 200，无需 SQL 补课时（证据见 L1-11 行）；全文搜索未做，教师入驻审核页面与独立工作台（数据看板）未做（教师入口寄居运营后台，见 4.2） | 2026-11-11 |
| M2 交易与学习 | 部分完成 | 购物车 API、下单幂等、0 元课、模拟支付、权益 outbox、学员播放签名（SPEC-LEARN-003 6/6）、进度回写已通；教师预览播放签名 2026-11-10 补齐（#204：只签本人课程、默认 10 分钟；#213 补过期重签）；真实支付宝台阶 1–5 代码已落地，但真实沙箱支付与退款一笔未跑（桩级 + 单测），不等于已验证收款 | 2026-11-13 |
| M3 互动与后台 | 部分完成 | 运营审核、用户/订单查询、退款审核、看板概览、对账 CSV 导入与差异处置、日结接口已通；UGC、举报处置未做，对账缺自动拉账与运营后台页面 | 2026-11-10 |
| M4 AI MVP | 未开始 | `lp-ai-service` 仅有启动类与 `package-info` | 2026-11-09 |
| M5 Web 加固与上线 | 未开始 | `security-ci`、`supply-chain-ci`、`e2e-ci` 已存在；仍缺压测、staging 实跑、灰度与部署流水线，另有 3 条 Trivy CRITICAL（Spring Framework 6.2.19，Boot 4.1.2 升级后待重扫确认） | 2026-11-13 |
| M6 小程序端 | 部分完成 | 4 个页面 + 静默登录 + 购买引导；无评论，AppID 已提供并本机注入（未入库），未提审 | 2026-11-09 |
| M7 小程序审核与灰度 | 未开始 | 无提审与发布记录 | 2026-11-09 |

## 2.3 阶段结论

当前处于 **M2 后端链路可演示、M3 刚起步** 的阶段。可演示的闭环是：教师通过 API 建课并提交 → 运营在后台审核定价上架 → 学员在 Web 下单 → 模拟支付回调 → 权益发放 → 播放签名 → 进度回写 → 学员发起退款申请 → 运营后台审核通过 → 模拟渠道退款成功并回收权益。

该闭环成立的前提是**本地模拟支付**（`LP_PAY_MOCK_ENABLED=true`），并且教师入驻审核目前只有后端接口、没有界面。真实支付宝台阶 1–5 代码已落地但真实沙箱一笔未跑，UGC、AI、小程序评论、部署流水线未完成；教师学习数据看板已落地（SPEC-LEARN-009，2026-11-13）；监控栈已有本地编排与抓取配置，告警求值证据见 L1-10，详见第 4 章。

## 2.4 规格条目索引（条目 → 实现状态）

状态与层级取自对应域文件；AC 完成度按 `develop` `b22ab7c` 的代码与页面行为在 2026-11-09 逐条核对，此后新增条目在各自 PR 分支核对，日期记在对应规格条目。格式为「已满足 AC 数 / 该条目 AC 总数」。`进行中` 但 AC 已全满足的条目表示仍有非 AC 事项（端到端接线或界面）未完成。

| 规格条目 | 能力 | 状态 | 层级 | AC 完成度 |
|---|---|---|---|---|
| SPEC-COURSE-001 | 教师建课与课程归属 | 已完成 | — | 4/4 |
| SPEC-COURSE-002 | 可编辑范围与改稿回退 | 已完成 | — | 4/4 |
| SPEC-COURSE-003 | 提交审核 | 已完成 | — | 4/4 |
| SPEC-COURSE-004 | 运营审核、定价与首次上架 | 已完成 | — | 5/5 |
| SPEC-COURSE-005 | 教师下架与再上架 | 未开始 | L2-3 | 0/4 |
| SPEC-COURSE-006 | 运营强制下架 | 未开始 | L2-5 | 0/4 |
| SPEC-COURSE-007 | 公开展示与访问控制 | 已完成 | — | 4/4 |
| SPEC-COURSE-008 | 内容结构与删除限制 | 进行中 | L2-3 | 3/4 |
| SPEC-COURSE-009 | 教师入驻审核 | 已完成 | — | 5/5 |
| SPEC-COURSE-010 | 展示字段与长度约束 | 已完成 | — | 5/5 |
| SPEC-COURSE-011 | 定价口径 | 已完成 | L1-2 | 5/5 |
| SPEC-COURSE-012 | 教师撤回待审 | 已完成 | — | 4/4 |
| SPEC-TRADE-001 | 下单与幂等 | 已完成 | — | 12/12 |
| SPEC-TRADE-002 | 0 元订单 | 已完成 | — | 5/5 |
| SPEC-TRADE-003 | 优惠券校验与核销 | 已完成 | — | 4/4 |
| SPEC-TRADE-004 | 支付发起与重付约束 | 进行中 | L2-2 | 5/7 |
| SPEC-TRADE-005 | 支付回调与三态处理 | 已完成 | — | 7/7 |
| SPEC-TRADE-006 | 订单取消 | 已完成 | — | 4/4 |
| SPEC-TRADE-007 | 超时关单 | 已完成 | L1-3 | 5/5 |
| SPEC-TRADE-008 | 退款准入 | 已完成 | — | 6/6 |
| SPEC-TRADE-009 | 退款审核与状态机 | 已完成 | — | 7/7 |
| SPEC-TRADE-010 | 退款失败与补偿 | 已完成 | — | 3/3 |
| SPEC-TRADE-011 | 退款成功后的权益处理 | 已完成 | — | 4/4 |
| SPEC-TRADE-012 | 购票（Web 购买、小程序引导） | 进行中 | L2-2 | 4/4 |
| SPEC-TRADE-013 | 金额单位与边界约束 | 已完成 | — | 5/5 |
| SPEC-TRADE-014 | 支付对账与差异处置 | 进行中 | 真实收款前置 | 3/4 |
| SPEC-LEARN-001 | 权益发放与幂等 | 已完成 | — | 4/4 |
| SPEC-LEARN-002 | 退款后的权益撤销 | 已完成 | — | 4/4 |
| SPEC-LEARN-003 | 播放凭证与访问控制 | 已完成 | — | 6/6 |
| SPEC-LEARN-004 | 进度上报与有效观看时长 | 已完成 | — | 6/6 |
| SPEC-LEARN-005 | 完课判定（双 90%） | 已完成 | — | 6/6 |
| SPEC-LEARN-006 | 我的课程列表 | 已完成 | — | 5/5 |
| SPEC-LEARN-007 | 课程进度查询 | 已完成 | — | 4/4 |
| SPEC-LEARN-008 | 退款准入进度汇总 | 已完成 | — | 5/5 |
| SPEC-LEARN-009 | 教师学习数据看板 | 已完成 | — | 8/8 |
| SPEC-PLAT-001 | 网关身份头剥离与注入 | 已完成 | — | 4/4 |
| SPEC-PLAT-002 | 端来源识别 | 未开始 | L2-1 | 0/4 |
| SPEC-PLAT-003 | 小程序登录与凭证防重放 | 已完成 | — | 5/5 |
| SPEC-PLAT-004 | 账号注销冷静期 | 进行中 | L1-8 | 32/33 |
| SPEC-PLAT-005 | 服务间内部调用鉴权 | 已完成 | — | 4/4 |
| SPEC-PLAT-006 | 视频上传校验与交付 | 已完成 | L1-11 | 6/6 |
| SPEC-PLAT-007 | AI 模型接入（BYO） | 未开始 | 上线后首批 | 0/6 |
| SPEC-PLAT-008 | 统一响应与错误码分段 | 已完成 | — | 5/5 |
| SPEC-PLAT-009 | 分页约定 | 已完成 | — | 5/5 |
| SPEC-PLAT-010 | 统一操作审计（三类审核） | 已完成 | L1-7 | 10/10 |
| SPEC-PLAT-011 | 小程序登录频控 | 已完成 | L1-6 | 8/8 |
| SPEC-PLAT-012 | 网关基础限流 | 已完成 | L1-6 | 8/8 |
| SPEC-PLAT-013 | 网关 CORS 白名单 | 已完成 | L1-6 | 7/7 |

## 2.5 L1 上线硬门槛清单

以下十一项为上线硬门槛，编号由负责人给定，其中 L1-11 为 2026-11-10 后续补入。对应规格、状态与证据以 2.4、第 3 章与第 4 章为准；本表只汇总，不修改既有条目结论，口径不一致处在「核对说明」写明。

| 编号 | 门槛 | 对应规格 | 状态 | 核对说明 | 最后核对 |
|---|---|---|---|---|---|
| L1-1 | 退款结果未知 | SPEC-TRADE-009（7/7）、SPEC-TRADE-010（3/3） | 已完成 | 代码先提交状态 1 再调渠道：`RefundServiceImpl.review` 的事务内只执行 `markApproved`（置 1），事务提交后才在 `settleApproved` 调用渠道；用例 `RefundServiceImplTest#approvedStatusIsVisibleWhileChannelIsStillRunning`；原 4.2 的过期缺口已删除 | 2026-11-10 |
| L1-2 | 金额边界与勾稽 | SPEC-TRADE-013（5/5）、SPEC-COURSE-011（5/5） | 已完成 | 金额单位与订单/支付/退款三处勾稽已完成；教师端拟定价入口于 2026-11-11 浏览器实跑（建课 88.80 → 提交 → 撤回 → 改价 66.60 → 再提交），AC-1 满足；元/分双轨仍为技术债，见 4.2 | 2026-11-11 |
| L1-3 | 订单超时关单 | SPEC-TRADE-007（5/5） | 已完成 | L1-3 层级已从 SPEC-TRADE-004 移到实际承载它的 SPEC-TRADE-007；004 剩 2 条 AC 属「支付发起与重付」业务缺口，层级改回 L2-2，见 4.2 | 2026-11-10 |
| L1-4 | 幂等契约 | SPEC-TRADE-001（12/12） | 已完成 | `Idempotency-Key`、最终响应原文缓存、40901 与 40902 已实现；2026-11-13 合并下单把幂等摘要扩展到「课程集合 + 顺序无关 + 券 + 金额」，见 AC-8 至 AC-12；Redis 降级窗口的同键换体限制见 4.2 | 2026-11-13 |
| L1-5 | 教师入驻审核 | SPEC-COURSE-009（5/5） | 已完成（接口） | 接口完成、管理端页面未做：审核接口、权限码 `admin:teacher:audit` 与重新提交已通，`apps/admin` 无审核页，不能读作可交付；档案未打 L1-5 层级标记 | 2026-11-13 |
| L1-6 | 登录风控与网关边界 | SPEC-PLAT-011（8/8）、SPEC-PLAT-012（8/8）、SPEC-PLAT-013（7/7） | 已完成 | 小程序登录频控、网关限流与 CORS 白名单三项 AC 全满足 | 2026-11-10 |
| L1-7 | 统一操作审计 | SPEC-PLAT-010（10/10） | 已完成 | 三类审核的统一审计已完成，结算作废（动作 6 / 对象 3）已纳入同一审计链路（验收见 SPEC-TRADE-014 AC-3）；原同层级的账号注销已按门槛编号移到 L1-8 | 2026-11-10 |
| L1-8 | 账号注销 | SPEC-PLAT-004（32/33） | 进行中 | 已实现 32 条：AC-1/2/3/4/5/6/7/8/9/10/11/12/13/14/15/16/17/18/19/20/21/23/24/25/26/27/28/29/30/31/32/33，覆盖申请与撤销、7 天冷静期落库、重复申请幂等、阻塞失败关闭、员工账号拒绝、查询与状态门禁、trade 冷静期守卫、执行任务与匿名化、环境变量与指标。2026-11-10 逐条复核结论：AC-6、AC-9、AC-17 的单测断言与 AC 逐条对应（`AccountCancellationServiceImplTest#applyAfterWithdrawalCreatesANewRequestWithANewDeadline`、`OrderTimeoutConcurrencyTest#timedOutOrderNoLongerBlocksAccountDeletion`、`AccountAnonymizationServiceTest#anonymisationRewritesInPlaceAndDeletesNoBusinessRow`，均随 `backend-test` 执行），可判满足；AC-16 由 `lp-trade-service` 的 `NewAccountVisibilityTest`（订单查询按新用户 id 为空、原用户 1 条）与 `lp-learn-service` 的 `NewAccountVisibilityTest`（`hasEntitlement`/`ownedCourseIds` 按新用户 id 为空、原用户有该课程）显式补断言，AC-25 由 `AccountCancellationServiceImplTest#applyAndWithdrawLogTheDeadlineAndSource` 与 `AccountCancellationExecutionJobTest#everyOutcomeIsLoggedWithUserDeadlineAndSource` 覆盖申请/撤销/执行/跳过/失败五处日志（断言 userId、effectiveAt、source），两条闭环；AC-33 已闭环：`LpAuthAccountCancellationCacheInvalidateFailed` 改为 `increase(...[15m]) >= 3` + `for: 5m`（门槛 3 与 5 分钟去抖未动），`scripts/observability-ac33-drill.sh` 改回单批 3 次失败即跑通 pending（采样中 `activeAt` 未后移）→firing→Alertmanager active→inactive，见 `docs/local-dev-guide.md` 4.10；原 `[5m]` 与 `for: 5m` 等长会让单批突发永不触发，已按「不降门槛、改窗口」裁定修正；唯一仍为「部分」的是 AC-22：Web 侧已于 2026-11-10 用真实账号浏览器实跑（申请/倒计时/撤销/67003 常驻提示/67005 下单与支付门禁），小程序真机预览尚未开始（AppID 已由负责人提供并注入本机、未入库，缺网关可达与真机执行），见 §4.3 外部阻塞 | 2026-11-10 |
| L1-9 | 支付对账 | SPEC-TRADE-014（3/4） | 进行中 | 已完成：CSV 账单导入与 `uk_channel_date_type` 幂等覆盖、差异明细落库、ERROR 日志与 `reconcile_diff_total{channel}`、日查询、重跑与人工标记（AC-1/AC-2）；`payment_settle_batch`（V12，V13 改为 `uk_active_day`）、日结/作废/详情/列表接口与账单日锁定（AC-3）。AC-3 走的是「作废解锁」路径，不是「关闭后不可变」：作废仅 `ADMIN`（`admin:reconcile:close`）、原因必填（1–300 字）、与统一审计（动作 6 / 对象 3）同事务写入；作废行保留可查（`status=2`），账单日解锁后重新日结插入新批次，不复活原批次。剩余：AC-4 仍因真实收款（支付宝商户号与账单权限）未开而阻塞；运营后台无页面、导出不做，见 4.2。自动拉账待商户号与账单权限 | 2026-11-10 |
| L1-10 | 发布前工程化验证 | 无对应规格条目 | 进行中 | 依赖与镜像漏洞扫描已进 CI（`.github/workflows/supply-chain-ci.yml`，Trivy 固定版本，非阻断基线期，报告落 artifact）；备份恢复演练脚本已落地并在本地跑通（`scripts/backup-drill.sh`，逻辑备份、无 binlog PITR、耗时不代表生产 RTO）；后端依赖基线已升到 Boot 4.1.2 + Cloud 2025.1.4（提交 `20ed349`，托管 Spring Framework 7.0.10），BouncyCastle/minio 等以显式 `dependencyManagement` 钉在修复版本；3.5.16 基线实测 Trivy HIGH 清空、CRITICAL 由 15 条降到 3 条（Spring Framework 6.2.19，3.5 线无修复版本），升级后这 3 条是否清零以重扫报告为准；E2E 冒烟已进独立 workflow（`e2e-ci.yml`，不进五项必需检查，失败上传六项取证）；监控栈已落 `infra/observability`（Prometheus / Alertmanager / Grafana，`rule_files` 挂载现有规则文件），8 个服务均暴露 `/actuator/prometheus` 并被抓取（2026-11-10 本地 8 个服务 8/8 UP、规则组 `health=ok`）；AC-33 的 firing、Alertmanager 可见与回 inactive 已由 `scripts/observability-ac33-drill.sh` 本地跑通（`increase(...[15m]) >= 3` + `for: 5m`，单批 3 次失败注入），见 3.5 与第 5 章 | 2026-11-13 |
| L1-11 | 内容生产通路（上传→处理→播放链路，教师端或运营后台任一） | SPEC-PLAT-006（6/6） | 已完成 | 上传会话、浏览器单次 PUT 直传、确认回填、运营后台教师入口与播放预览已成：`apps/admin` 的 `/lesson-videos` 走「建会话→PUT→确认→列表回显」，2026-11-10 用 6 秒 MP4 在本地全栈实跑通过（PUT 200、confirm 200、`course_lesson` 回填 640×360 与字节数、会话置已确认）；AC-6 由 `GET /api/v1/teacher/courses/{courseId}/chapters/{chapterId}/lessons/{lessonId}/play-url` 提供，教师只能签自己课程、默认 10 分钟、只回 `playUrl/expiresIn/expiresAt`，目录改用 `hasVideo` 不再下发对象键；`submit` 要求每条可见课时均已确认视频。播放预览已于 2026-11-10 在本地全栈用 Chrome 实跑（见下方「L1-11 播放预览证据」）：草稿课可预览、签名地址取对象 206、播放器 `currentTime≈3.57s/6s`、`640×360`；非本人课程返回 `40301`，不存在课程返回 `40401`。`deleted=1` 课程路径目前只有代码依据（`selectById` 带 `deleted = 0`），未实测。转码仍未做，属后续独立切片。**2026-11-11 从零跑通教师端自助链路**：浏览器新建课程（99.00/129.00）→ 行内「课时」跳到 `/lesson-videos?courseId=` → 新增章节 → 新增课时 → 上传 6 秒 MP4 并确认（`hasVideo=true`、`durationSec=6`、H264 320×240）→ 编辑课时标题与排序 → 提交审核（待审核），全程无 SQL；转码展示位已在列表预留，字段未上线时显示 `--` | 2026-11-11 |

**L1-11 播放预览证据（2026-11-10）**：Chrome 以 `--host-resolver-rules=MAP minio 127.0.0.1` 启动（未改 `/etc/hosts`），`demo_teacher_lin` 登录 `apps/admin` → 草稿课 990012（`status=0`）→ 课时 990285（6 秒、640×360）→ 预览。`play-url` 返回 HTTP 200（响应含 `playUrl`、`expiresIn=600`、`expiresAt`）；签名地址 host 为 `minio:9000`、`X-Amz-Expires=600`；`video/mp4` 取对象 206，播放器 `readyState=4`、`currentTime≈3.57s`、`duration=6`、`videoWidth×videoHeight=640×360`，无 media error。**前置条件**：本地浏览器必须能解析 `minio`（hosts 或上述 Chrome 参数），生产必须由公网 HTTPS 媒体域名签发，不能沿用容器内主机名（见 4.2）。

---

# 3. 已做

## 3.1 后端与网关

| 能力 | 完成程度与限制 | 证据 | 最后核对日期 |
|---|---|---|---|
| 账号与 JWT | 注册、密码登录、刷新、登出、`/me` 可用；密码 BCrypt 存储；注销已有申请、撤销与查询接口（冷静期 7 天，`effective_at` 申请时落库），并已提供内部冷静期状态查询与提交后异步失效 trade 缓存；执行任务与匿名化已实现（`LP_AUTH_ACCOUNT_DELETION_EXECUTION_ENABLED` 默认关闭，staging/prod 显式开启），Web `/account` 与小程序账号页注销入口已上线 | `lp-auth-service/.../controller/AuthController.java`、`controller/AccountCancellationController.java`、`controller/InternalAccountCancellationController.java`、`service/impl/AccountCancellationServiceImpl.java`、`client/AccountDeletionCacheClient.java` | 2026-11-10 |
| 小程序 code 登录 | 接口与真实 `jscode2session` 客户端均已实现，默认关闭；本地可用 mock 客户端；登录入口按真实 IP 与 `X-Device-Id` 双维度频控，Redis 故障放行并打点 | `AuthController.java`、`security/HttpWechatMiniappClient.java`、`security/MiniappLoginRateLimitFilter.java`、`config/MiniappProperties.java` | 2026-11-10 |
| 网关 | 7 条服务路由、身份头剥离与注入、JWT 三态校验，默认 `hard`；`/api/v1/**` 按用户或真实客户端 IP 做每实例内存限流（默认 300 次/分钟/实例，集群容量 = 阈值 × 副本数），超限返回 429 + `Retry-After` + 统一信封；CORS 走显式来源白名单，预检在鉴权前处理，非 dev 未配置来源时启动失败；`/internal/**` 与 `/actuator/**` 不经路由 | `lp-gateway/src/main/resources/application.yml`、`application-dev.yml`、`filter/HeaderSanitizeFilter.java`、`filter/JwtAuthFilter.java`、`filter/GatewayRateLimitFilter.java`、`config/CorsConfig.java` | 2026-11-10 |
| 课程浏览 | 已上架课程分页、分类、关键词（`LIKE` 标题/副标题）、详情、目录；下架课程对非持有者按 404 处理 | `lp-course-service/.../controller/CoursePublicController.java`、`mapper/CourseMapper.java` | 2026-11-09 |
| 教师课程 API | 建课、改课、提交审核、章节课时增删改、状态计数；按 `owner_teacher_id` 做归属校验；建课与改课在服务层校验教师入驻审核状态，未通过返回 `61006` 且不写课程 | `controller/TeacherCourseController.java`、`service/impl/TeacherCourseServiceImpl.java` | 2026-11-09 |
| 课时内容清理 | 删除课时或章节时在同一事务内把 `video_url` 登记进 `lp_cms.lesson_content_cleanup`（V11），由 course-service 内 ticker 每 30s 异步删除——原 §5「MinIO 孤儿对象清理」建议已落地，归属本节。每批 20 行、最多 5 次尝试（30s→1m→2m→4m，约 7.5 分钟转终态），单行失败隔离。ticker 按单实例调参，多副本不会重复删同一对象，扩容前需分布式锁；指标与告警见 `docs/local-dev-guide.md` 4.13。 | `db/migration/course/V11__lesson_content_cleanup.sql`、`cleanup/LessonContentCleanupWorker.java`、`LessonCleanupScheduler.java`、`LessonCleanupMetrics.java`、`mapper/LessonContentCleanupMapper.java`、`application.yml:76-94` | 2026-11-11 |
| 课程审核 | 待审队列、通过并定价上架、驳回、审核审计查询；审核动作同时写入统一审计表（含操作人角色、前后状态、traceId），与状态更新同事务 | `lp-admin-service/.../controller/AdminCourseController.java`、`db/migration/course/V4__course_review_audit.sql`、`db/migration/course/V7__admin_audit_log.sql` | 2026-11-10 |
| 教师入驻审核 | 审核字段与存量回填、按状态筛选的审核队列、通过/驳回（驳回原因必填）、审核留痕（操作人、时间、前后状态）、教师查询与重新提交；权限码 `admin:teacher:audit`；审核动作同时写入统一审计表并与状态更新同事务。**驳回原因超过 300 字被静默截断（见 4.2）** | `db/migration/course/V6__teacher_audit.sql`、`service/impl/TeacherAuditServiceImpl.java`、`lp-admin-service/.../controller/AdminTeacherController.java` | 2026-11-10 |
| 统一操作审计 | 三类审核动作（课程/退款/教师）写入 `lp_cms.admin_audit_log`；课程与教师审核同事务写入，退款审核经 `lp_oms.audit_outbox` 至少一次投递、`dedupe_key` 去重；查询接口 `GET /api/v1/admin/audit-logs` 支持按对象与按操作人+时间，权限码 `admin:audit:list`；`course_review_audit` 保留双写、历史数据不迁移 | `db/migration/course/V7__admin_audit_log.sql`、`db/migration/trade/V10__audit_outbox.sql`、`lp-course-service/.../service/AdminAuditService.java`、`lp-trade-service/.../audit/AuditOutboxRelay.java`、`lp-admin-service/.../controller/AdminAuditController.java` | 2026-11-10 |
| 交易 | 购物车 API、下单幂等、0 元课直接完成、订单列表/详情/取消、超时关单（30 分钟，条件更新与支付回调竞争安全，同时关闭待支付记录并释放优惠券）；优惠券在下单时校验并核销；下单/支付/退款统一 `Idempotency-Key`（Redis 占位与结果重放，同键不同体 40901，处理中超时 40902，成功结果缓存 24h、支付发起 30 分钟） | `lp-trade-service/.../controller/OrderController.java`、`service/impl/OrderServiceImpl.java`、`job/OrderTimeoutJob.java`、`idempotency/IdempotencyService.java` | 2026-11-09 |
| 支付 | 支付发起（`Idempotency-Key` 必填，同键并发复用首次预支付结果）、`/api/v1/payments/alipay/notify` 回调入口、金额与渠道校验、重复回调幂等；**真实渠道未配置时统一返回 50205**，本地由 mock 渠道替代 | `payment/MockPaymentGateway.java`、`payment/UnconfiguredPaymentGateway.java`、`controller/PaymentController.java`、`idempotency/IdempotencyService.java` | 2026-11-09 |
| 退款 | 准入校验（付款后 7 天内且学习进度 ≤ 20%，超限返回 `62007` 且不落单）、生成待审核单（不自动退款）、运营审核、驳回需原因并可再次申请、审核通过后原渠道退款、权益软失效、撤销 outbox；渠道明确拒绝落 4 并放回可申请，调用超时等结果未知落 5 且订单保持退款中，learn 进度不可用时失败关闭返回 `50201` | `service/impl/RefundServiceImpl.java`、`db/migration/trade/V7__refund_review.sql`、`db/migration/trade/V9__refund_unknown_result.sql`、`controller/RefundInternalController.java` | 2026-11-09 |
| 权益与 outbox | 本地消息表、定时中继、指数退避、死信、运营重放、Prometheus 指标 | `entitlement/EntitlementRelay.java`、`entitlement/OutboxAdminService.java`、`controller/InternalEntitlementOutboxController.java` | 2026-11-09 |
| 学习交付 | MinIO 预签名播放地址、免费课时试看、我的课程（含已退款标记）、进度上报与查询；**完课按有效观看 ≥ 90% 与播放位置 ≥ 90% 双条件置位（D7 已实现，见 SPEC-LEARN-005）** | `lp-learn-service/.../controller/LessonController.java`、`service/impl/PlayTokenServiceImpl.java`、`service/impl/LearningProgressServiceImpl.java` | 2026-11-09 |
| 运营查询 | 用户列表、订单列表、菜单树、7 日看板概览 | `lp-admin-service/.../controller/AdminUserController.java`、`AdminOrderController.java`、`AdminMenuController.java`、`AdminMetricsController.java` | 2026-11-09 |
| 数据迁移 | 七个库的 Flyway 目录；auth/course/trade/learn 有业务表；ugc/ai/ops 仅有 V1 基线 | `db/migration/**` | 2026-11-09 |

## 3.2 Web 学员端

| 能力 | 完成程度与限制 | 证据 | 最后核对日期 |
|---|---|---|---|
| 课程首页与列表 | 分类筛选、关键词、分页、空态与错误态 | `apps/web/src/views/CourseListView.vue` | 2026-11-09 |
| 课程详情与结算 | 详情、目录、立即购买、创建订单、跳转支付参数 `payUrl`；按钮文案写支付宝，但真实渠道未接 | `CourseDetailView.vue`、`CheckoutView.vue` | 2026-11-09 |
| 订单结果与订单列表 | 支付结果页、订单列表、状态展示；已支付订单可发起退款申请，展示待审核/处理中/已退款/已驳回/失败及原因，驳回或失败后可重新申请 | `OrderResultView.vue`、`OrdersView.vue` | 2026-11-09 |
| 我的课程与播放 | 断点续播、进度回写、退款课程标记，页头提供退款入口跳转我的订单 | `MyCoursesView.vue`、`PlayerView.vue` | 2026-11-09 |
| 登录注册与续期 | 账号密码登录、注册、token 刷新、回跳 | `LoginView.vue`、`api/auth.ts` | 2026-11-09 |
| 购买 ticket | 可凭 ticket 兑换会话并进入购买页；**小程序端未签发 ticket，该入口当前无生产者** | `PurchaseView.vue`、`utils/purchase.ts` | 2026-11-09 |
| 未做 | 购物车页面、优惠券入口、评论、收藏、举报、资料维护 | `apps/web/src/router.ts` 路由表 | 2026-11-09 |

## 3.3 运营后台

| 能力 | 完成程度与限制 | 证据 | 最后核对日期 |
|---|---|---|---|
| 运营登录 | OPS/ADMIN 登录与角色校验 | `apps/admin/src/views/LoginView.vue` | 2026-11-09 |
| 看板 | 7 日 GMV、订单数、支付率、新增用户、销量 Top5 | `DashboardView.vue`、`api/admin.ts` | 2026-11-09 |
| 课程审核 | 待审队列、通过并定价、驳回、审核记录 | `ReviewQueueView.vue`、`api/admin.ts` | 2026-11-09 |
| 用户与订单 | 关键词/订单号查询、分页列表 | `UsersView.vue`、`OrdersView.vue` | 2026-11-09 |
| 退款审核 | 按状态筛选的退款队列、通过/驳回（驳回必填原因）、分页与操作错误提示 | `RefundQueueView.vue`、`controller/AdminRefundController.java` | 2026-11-09 |
| 教师入驻审核接口 | 按状态筛选的审核队列、通过/驳回（驳回必填原因）、权限码 `admin:teacher:audit`；**无运营页面**，仅接口可用 | `controller/AdminTeacherController.java`、`service/impl/AdminTeacherAuditServiceImpl.java` | 2026-11-09 |
| 教师学习数据 | 教师本人课程的学习看板：KPI、每日活跃、课程明细、六档完课分布与课时完成率；观看时长为累计值，完课率分母为在学学员；ADMIN 只读本人教师档案名下课程 | `TeacherAnalyticsView.vue`、`api/teacherAnalytics.ts`、`controller/TeacherAnalyticsController.java` | 2026-11-13 |
| 未做 | 教师入驻审核页面、课时删除与拖拽排序（课时新增/编辑已可用）、评论与举报处置、角色与菜单管理 | 教师课程页（建课/改价/提交/撤回）、课时视频页（章节/课时新增与编辑、上传）、学习数据页已可用 | 2026-11-13 |

## 3.4 微信小程序

| 能力 | 完成程度与限制 | 证据 | 最后核对日期 |
|---|---|---|---|
| 页面 | 首页、课程详情、我的课程、播放页 | `apps/miniapp/app.json` | 2026-11-09 |
| 登录 | `wx.login` 静默换取 token，失败时展示后端错误 | `apps/miniapp/utils/auth.js` | 2026-11-09 |
| 购买引导 | 课程页复制 Web 购买链接，提示到浏览器购买；**未调用购买 ticket 接口** | `apps/miniapp/pages/course/course.js` | 2026-11-09 |
| 未做 | 评论、AppID 入库配置（当前仅本机注入）、真机验收、提审与发布、`miniprogram-ci` 上传 | `apps/miniapp/project.config.json`、`.github/workflows/frontend-ci.yml` | 2026-11-09 |

## 3.5 基础设施、脚本与 CI

| 能力 | 完成程度与限制 | 证据 | 最后核对日期 |
|---|---|---|---|
| 本地一键起停 | Compose 拉起 MySQL/Redis/RocketMQ/MinIO/Nacos、7 个后端服务、Web 与后台；构建 jar 与前端、执行迁移、导入种子 | `infra/docker-compose.yml`、`scripts/dev-up.sh`、`scripts/dev-down.sh` | 2026-11-09 |
| 演示数据 | 教师/运营/学员/管理员账号、多状态课程与一笔已支付订单 | `db/seed/demo-seed.sql`、`scripts/seed/seed-demo.sh` | 2026-11-09 |
| 验收与演练 | 冒烟、outbox 故障演练、退款闭环演练、注销执行双副本并发演练、一条命令验收；支付发起与退款申请的 `Idempotency-Key` 头已补齐，`verify-all.sh` 可在本机全绿；账号注销执行的双副本并发已本地跑通（5 条到期行由两副本 4+1 执行，`executed` 合计 5，同一 `requestId` 未出现两次） | `scripts/smoke.sh`、`scripts/outbox-fault-drill.sh`、`scripts/refund-drill.sh`、`scripts/auth-replica-race-drill.sh`、`scripts/verify-all.sh` | 2026-11-10 |
| CI | `backend-build`/`backend-test`、`frontend-check`、`miniapp-check`、`docs-check`；`e2e` 为独立 workflow，不进五项必需检查 | `.github/workflows/backend-ci.yml`、`frontend-ci.yml`、`docs-ci.yml`、`e2e-ci.yml` | 2026-11-10 |
| 依赖与镜像漏洞扫描 | Trivy 固定 0.76.2，扫 Maven `pom.xml`、`pnpm-lock.yaml` 与基础镜像；基线期只报告不阻断，不进五项必需检查；基础镜像周扫在 `schedule` 与手动触发时跑；`.trivyignore.yaml` 规定每条必须带 `id`/`paths`/`statement`/`expired_at`。2026-11-10 在 Boot 3.5.16 + Cloud 2025.0.3 基线上补齐 jackson/guava/minio/snappy 补丁后实测 unique 3 条（CRITICAL 3 / HIGH 0），升级前为 unique 76 条（CRITICAL 15 / HIGH 61），剩余 critical 均为 Spring Framework 6.2.19 且只有 7.0.9 修复版本；2026-11-13 已升到 Boot 4.1.2 + Cloud 2025.1.4（提交 `20ed349`，托管 Framework 7.0.10），这 3 条是否清零以重扫报告为准 | `.github/workflows/supply-chain-ci.yml`、`.trivyignore.yaml` | 2026-11-13 |
| 备份恢复演练 | `mysqldump` 七个库 → 隔离恢复实例（`--rm`、无卷、临时端口）→ 比对表清单、逐表行数、`CHECKSUM TABLE`、`flyway_schema_history` 最大版本；MinIO `mc mirror --preserve` 双向（桶→宿主机备份树→新桶）→ 比对对象数、字节数与 ETag。边界：逻辑备份、RPO = 两次 dump 间隔且无 binlog PITR、本机耗时不代表生产 RTO | `scripts/backup-drill.sh` | 2026-11-10 |
| 本地钩子 | `pre-commit` 跑文档校验与 Spotless，`commit-msg` 跑提交信息校验 | `.githooks/pre-commit`、`.githooks/commit-msg`、`scripts/install-hooks.sh` | 2026-11-09 |
| 测试 | 47 个后端测试类、6 个前端 spec；端到端冒烟由 `e2e-ci.yml` 在 push、每日定时与手动触发时运行（PR 仅按路径触发），失败上传六项取证 | `lp-*/src/test/**`、`apps/*/src/**/*.spec.ts`、`.github/workflows/e2e-ci.yml` | 2026-11-10 |
| 未做 | staging/prod 部署。镜像构建与推送已具备：`release-images` 默认推 ghcr（未配 `REGISTRY*` 时回落 `ghcr.io/<owner>`）、支持 `platforms`（默认 `linux/amd64`，可选 `linux/arm64` 或多架构），但只在 `v*` tag 与手动 `workflow_dispatch` 触发，**不监听 develop**，也没有 deploy 作业，因此 staging 仍是手动发布 | `.github/workflows/release-images.yml:21-42`、`:51-62`；部署侧仍无清单 | 2026-11-11 |

---

# 4. 计划了但未做

## 4.1 阻塞于商户凭据/渠道接入

以下条目不取决于开发排期：缺真实商户凭据或渠道接入，凭据到位前无法实现与验证。

| 功能 | 文档出处 | 现状 | 卡点 | 最后核对日期 |
|---|---|---|---|---|
| 真实支付宝支付 | engineering-plan 1.2、5.5、13.2 M2；15.2 D2 | 台阶 1–5 全部落地（台阶 5 含 P6 退款执行/悬挂巡检/自动收敛与 P7 管理端渠道退款状态可视）；两个限定仍然成立：`refund-converge.enabled` 默认 `false`、自动收敛未生效，只有 `channel_done` 告警兜底；真实支付宝沙箱的支付与退款一笔未跑，全部验证为桩级 + 单测（`lp-trade-service` 23 个单测、`lp-admin-service` 26 个单测、`apps/admin` 28 个前端用例），仍不能闭环收款 | 缺商户私钥、公钥证书、公网回调地址；自动收敛默认关闭；支付与退款验证仅桩级，真实沙箱一笔未跑 | 2026-11-12 |
| 支付宝对账 | engineering-plan 1.2、13.2 M3、15.1 R11；SPEC-TRADE-014 | 已实现 CSV 账单导入（支付/退款）、`uk_channel_date_type` 幂等覆盖、差异明细与告警、日查询、重跑与人工标记（`/api/v1/reconcile/**`），以及日结、作废、详情、列表接口（`/api/v1/reconcile/settlements/**`，`payment_settle_batch`）；退款单只读对账（P6c）已落地：`RefundReconcileJob` 每 10 分钟扫 `status IN (1,5)` 的退款单，用 `refund_no` 调 `refundQuery`，只输出「渠道已完成/查不到/状态不符/金额不符/查询失败」差异，不改退款单、订单与权益；按 10 分钟→1 小时→6 小时退避、单轮上限 50、日志仅在结论变化时打；暴露 `lp_trade_refund_reconcile_{pending,channel_done,oldest_seconds}` 三个 gauge 与 `scanned`、`converged`、`skipped` 计数（`skipped` 为闸门拒绝，代表并发或数据不一致，需人工关注），`channel_done` 持续非零、指标缺失、最久悬挂超 24 小时均有告警（`infra/observability/prometheus-rules.yml`），告警文案为「存在已确认退款但未落账的退款单」，**不写「巡检发现」**——该指标也包含 review 路径刚写入、结算尚未完成的短暂非零；开关 `LP_PAY_ALIPAY_REFUND_RECONCILE_ENABLED` 默认 `true`，**单实例假设**，多实例会重复查询；未知结果自动收敛（P6d-2）已接入但开关 `LP_PAY_ALIPAY_REFUND_CONVERGE_ENABLED` **默认 `false`**，当前不生效；**无自动拉账任务，运营后台无日结页面** | 自动拉账依赖商户号与账单权限；自动收敛默认关闭，开启前该缺口靠 `channel_done` 告警兜底；**未完成对账能力不得开启真实收款** | 2026-11-12 |
| 支付宝回调 `app_id` 校验 | SPEC-TRADE-005 AC-6；engineering-plan 5.5、8.3 | 已实现：按 RSA2 → 签名 → `app_id` → `seller_id` 顺序校验（`e2e800b`），失败 `62016`、错渠道 `62010`；沙箱真实回调尚未实测 | 无代码阻塞；待沙箱真实回调联调 | 2026-11-11 |
| 小程序支付（JSAPI）与 `payer_openid` 落库 | product-lifecycle F07；engineering-plan 5.5、15.2 D2；SPEC-TRADE-012 | 无小程序支付实现；`payment_record.payer_openid` 当前无写入方，只有账号注销的匿名化会读取并覆写该列 | AppID 已提供（本机注入、未入库）；缺商户号与 APIv3 密钥；注销演练中该列只能由 SQL fixture 预置，属非接口写入，随小程序支付链路补齐 | 2026-11-10 |

> **真实支付宝支付的依据**：台阶 1 发起支付已落地（`e99cddd` 后端、`8cef57d` 前端）——`LP_PAY_ALIPAY_ENABLED=true` 时装配 `AlipayPaymentGateway`（`lp-trade-service/src/main/java/com/lp/trade/payment/alipay/AlipayPaymentGateway.java:48-49`）并生成自提交表单（同文件 `:112-143`），Web 端解析该表单并提交跳转（`apps/web/src/utils/payment.ts:72-108`），关闭时回落 `UnconfiguredPaymentGateway` 固定返回 `50205`。台阶 2 回调验签已落地（`e2e800b`）：`PaymentController` 以 `@RequestBody String` 取原始报文（超 16K 回 `413`），`AlipayNotifyForm` 单次解码、保留字段顺序并拒绝重名字段，`AlipaySignatureVerifier` 按 RSA2 → 签名 → `app_id` → `seller_id` 顺序校验，失败 `62016`、错渠道 `62010`，成功回 `success`、失败回 `failure`（`lp-trade-service/src/main/java/com/lp/trade/payment/alipay/AlipaySignatureVerifier.java`、`AlipayNotifyForm.java`、`.../controller/PaymentController.java:125-140`）；关闭开关时回落 `UnconfiguredSignatureVerifier` 固定返回 `50205`（`lp-trade-service/src/main/java/com/lp/trade/payment/UnconfiguredSignatureVerifier.java:44-52`）。台阶 3 回调入账已落地（`923ba31`）：验签通过后统一经 `applyConfirmedPayment` 入账（`f82d15c` 起回调与查单共用该入口，状态集合收敛为 `ChannelTradeState.PAID_STATUSES` 唯一来源），金额不一致、订单不存在、状态不可推进分别落 `payment_notify_anomaly`（`db/migration/trade/V14__payment_notify_anomaly.sql`，`reason` 1/2/3，`uk_channel_trade_reason` 保证重试幂等）；reason 1/3（含已关闭、已取消）记录并告警后仍回 `success`（ack 只表示已收到，不等于入账），reason 2 回 `failure` 让渠道重试（读副本延迟可能自愈，重试耗尽后由 T+1 对账按长款差异处理）。台阶 4 查单/关单已落地（`faf6853`、`f82d15c`、`9368650`、`280731d`）：`PaymentGateway` 新增 `query`/`close` 并有 Alipay、Mock、未配置三种实现，`PaymentChannelSyncService` 先向渠道关单、关单失败回落查单、渠道已付复用同一入账入口补单、关单成功但本地已付落 reason 3，`OrderTimeoutJob.scan()` 已接入且单笔 `62020`（渠道关单未确认）隔离不影响其他订单。台阶 5 退款执行已落地（P6b）：`refund()` 以 `out_request_no=refund_no` 调 `alipay.trade.refund`，只有 `fund_change=Y` 且退款金额与请求一致才算成功；否则用同一 `refund_no` 只读查询一次，查询失败、查不到、非成功或金额不一致一律落 `UNKNOWN` 交人工对账，绝不重发退款请求；`LP_PAY_ALIPAY_REFUND_ENABLED=false` 时在签名前固定返回 `50205`，支付、回调与关单不受影响。退款只读对账（P6c）已由 `RefundReconcileJob` 落地；未知结果自动收敛到已退款的结算入口（P6d-1）已落地为 `RefundSettlementService.settle(...)`，review 与对账任务共用同一入口、同一事务边界：入口内部重读退款单并校验状态仍为 1/5、`reconcile_finding = 1`、传入金额与 `refund.amount` 按分相等，条件 UPDATE 重复同样的谓词，任一不满足返回 `0` 且不写库、不抛异常，重复调用是无操作；定时任务接入（P6d-2）已落地：`RefundReconcileJob.inspect` 在对账查询确认成功后先落 `CHANNEL_DONE`，开关 `lp.pay.alipay.refund-converge.enabled`（环境变量 `LP_PAY_ALIPAY_REFUND_CONVERGE_ENABLED`，默认 `false`）为 `true` 时再调用同一 `settle(...)` 并把 `settledBy` 记为 `job`，成功一次计数 `lp.trade.refund.reconcile.converged`；闸门返回 `0`（行已推进或金额/状态不符）计数 `lp.trade.refund.reconcile.skipped` 并打 WARN，不当作正常无事发生；收敛抛异常只记日志、不中断整轮扫描。`CHANNEL_DONE` 由独立语句先提交，结算在另一事务里，失败下一轮只重试结算、不重复查询渠道。**默认关闭**，开启前置：staging 观察 `channel_done` 无长期误报、确认单实例部署、开关打开后人工验证至少一次真实收敛；关闭期间该缺口靠 `channel_done` 告警兜底。**字段语义**：`reconcile_finding = 1` 表示「渠道已确认退款、本地尚未推进」，与发现者是谁无关——review 路径也会写它，所以告警文案是「存在已确认退款但未落账的退款单」，不得写成「巡检发现」。**审计口径**：自动收敛**不写 `audit_outbox`**（该表要求 `operator_role`，自动收敛没有操作人），审计依据是退款单上的 `settled_by`（review 路径为 `review:{用户id}`，对账任务为 `job`）、`settled_at`，以及既有的 `reconcile_finding`、`reconcile_checked_at`、`reconcile_attempts`。**片号对应**：P6a 退款查询 `ebc4094`、P6b 退款执行 `f12d890`、P6c 悬挂巡检 `7703dde`/`3fb8a71`/`a607254`/`e9a0ba2`（10 分钟→1 小时→6 小时退避、单轮 50、三个 gauge 与三条告警）、P6d 自动收敛 `6c2a6bb`/`0109f29`/`2abf190`/`a6c9847`；P7 运营端渠道退款状态可视 `af44d31`（trade-service 只读接口）/`58a701b`（admin-service 代理）/`78897b8`（管理端展示）。**运营能力**：管理端退款队列可看到渠道侧退款状态（渠道退款状态、渠道退款金额、本地巡检结论），读取只查一次渠道、不改本地行；`NOT_FOUND` 表示渠道查无此单、结果未知，**不能认定为未退款**。**验证边界**：`RefundServiceImplTest` 的回滚用例用快照式事务管理器模拟，只证明 `revoke` 调用发生在结算事务内；真实回滚依赖 Spring 事务配置与 MySQL 行为，桩级用例覆盖不到，回滚路径尚无端到端验证。**截至目前支付与退款链路的全部验证都是桩级 + 单测：`lp-trade-service` 23 个单测（含新增 5 个渠道状态用例）、`lp-admin-service` 26 个单测、`apps/admin` 28 个前端用例全部通过，真实支付宝沙箱的支付与退款一笔未跑**（缺商户私钥、公钥证书、公网回调地址）。
>
> **台阶与开发片号的对应**（片号只出现在 PR 与提交信息里，正文一律用台阶）：台阶 1 发起支付 = P1 凭据加载 + P2 网页支付表单 + P2b Web 端提交与回跳；台阶 2 回调验签 = P3；台阶 3 入账 = P4；台阶 4 查单/关单/状态 = P5；台阶 5 退款 = P6+P7。

## 4.2 其他未做功能

| 功能 | 文档出处 | 现状 | 卡点 | 最后核对日期 |
|---|---|---|---|---|
| 日结与结算的运营后台页面与导出 | SPEC-TRADE-014 AC-3 与 Q-2；engineering-plan 5.2、5.5 | 接口已实现：日结、作废、详情、列表（`/api/v1/reconcile/settlements/**`），批次落 `payment_settle_batch`，日结后锁定账单日；口径已于 2026-11-10 冻结（只封账与净额汇总，不含手续费/分账/教师结算） | 运营后台无日结页面，本期只有接口；批次导出不做 | 2026-11-10 |
| 对账账单对象存储归档 | SPEC-TRADE-014 Q-3；engineering-plan 10.4 | 已定：维持库内 `payment_reconcile.raw_bill`，不迁 MinIO。账单是文本、单日行数量级小，对象存储归档属可选优化；**如单日账单体积上量再评估对象存储归档** | 无（按需评估，不阻塞上线） | 2026-11-10 |
| 已售课程的单个课时下架（停用课时） | SPEC-COURSE-008 AC-2；product-lifecycle 6.4 | 2026-11-11 起已售课程的课时禁止删除，未售课程可软删；单个课时「停用」未实现，本期只有拒绝 | 待定：需要课时级停用状态与学员侧下架提示口径；在冻结口径前不做，删除请求继续由 `61009` 兜底 | 2026-11-11 |
| 课时清理队列的引用索引（W-4） | V11 清理队列；`docs/local-dev-guide.md` 4.13 | 未建：worker 删除前要确认对象不再被 `course_lesson.video_url`（在用课时）与 `lesson_upload.object_key`（未确认上传会话）引用，两列均无索引，按对象键检查走全表扫；当前量级可接受，转码表已有 `uk_source_object`，不在缺口内 | 触发条件：单表 50 万行或单 tick 耗时 > 50ms；落地为 `V12__lesson_cleanup_reference_index.sql`（`course_lesson.idx_video_url`、`lesson_upload.idx_object_key`），新增索引属 expand 变更，回滚 `DROP INDEX` | 2026-11-11 |
| 评论、两级回复、收藏、举报与内容审核 | product-lifecycle F11/F12、4.3；engineering-plan 13.2 M3 | `lp-ugc-service` 仅有启动类与 `package-info`，`lp_ugc` 仅有 V1 基线 | 未排期；依赖 UGC 表设计与审核链路 | 2026-11-09 |
| AI 课程画像、审核、文案、学伴、BYO 模型配置 | product-lifecycle F15–F18；engineering-plan 第 7 章、13.2 M4 | `lp-ai-service` 仅有启动类与 `package-info`，`lp_ai` 仅有 V1 基线 | 未排期；缺客户模型 key 与配置界面 | 2026-11-09 |
| 购物车页面 | product-lifecycle F07、4.3 | 后端 `/api/v1/cart` 可用，Web 无购物车页面 | 未排期 | 2026-11-09 |
| 优惠券发券与领券 | product-lifecycle F07 | 只有 `coupon`/`user_coupon` 表与下单核销逻辑，无发券、领券、券列表接口 | 未排期；无运营配置入口 | 2026-11-09 |
| 教师入驻审核页面 | product-lifecycle F04；engineering-plan 13.2 M1 | 审核接口、权限码与教师重新提交接口已通；运营后台与教师端均无页面 | 未排期；依赖后台与教师工作台界面 | 2026-11-09 |
| 教师审核驳回原因超 300 字静默截断 | SPEC-PLAT-010、`AdminAuditService.record` | 驳回原因写入审计前按 300 字截断，接口不报错也不提示；超过 300 字时审计备注与实际驳回原因不一致 | 已知问题；处置方向：接口校验拒绝，超过 300 字返回 `40001` 并说明上限，不静默截断；不放宽 `remark` 字段 | 2026-11-10 |
| 教师工作台数据看板 | engineering-plan 1.2、2.1；product-lifecycle F13；SPEC-COURSE-011 | 教师建课/改价/提交/撤回页面已可用（`/teacher-courses`）；仍无销售/学习/评论统计接口与看板 | 未排期；缺 `course_daily_stat` 等汇总表 | 2026-11-11 |
| 教师提交拟定价（前端入口） | SPEC-COURSE-011 AC-1、SPEC-COURSE-012 | 教师端已有建课/改价页面（`/teacher-courses`）：`price`/`originalPrice` 以元提交，仅草稿（0）/已下架（3）可改，待审核（1）与已上架（2）只读并给出状态提示；待审行提供「撤回」入口（确认弹窗，仅状态 1 可点），撤回后回到草稿可重新改价提交；运营审核通过时以 `priceFen` 定最终价；建课与改价表单已含副标题、简介与难度（难度 1/2/3 与后端枚举一致，前端不另造校验）；2026-11-11 用 Chrome 实跑「建课 → 提交审核 → 撤回 → 改价并清空划线价 → 再提交」，行内提交在表单未保存时禁用 | 已可用；价格变更留痕复用审核审计（见下方技术债）；已购订单按快照价，不补差价、不退差价 | 2026-11-11 |
| 教师入驻审核只有接口，没有审核页面 | SPEC-COURSE-009；L1-5 | 审核接口、权限码 `admin:teacher:audit` 与重新提交已通；运营后台无审核页面，教师端也没有入驻入口与状态页 | 接口级完成，L1-5 的「已完成」只覆盖 AC，不代表可用；页面未排期 | 2026-11-10 |
| 视频上传与 ffprobe 校验 | product-lifecycle F06；engineering-plan 15.2 D5 | 上传会话（`upload-sessions`）、MinIO 直传与确认（`confirm`）接口已通（1c-1/1c-2）；运营后台已提供按 TEACHER 角色显示的课时视频上传页（1c-3b-2） | 教师独立工作台界面未排期，当前入口寄居运营后台 | 2026-11-10 |
| 前端上传上限是服务端配置的镜像常量 | `CourseUploadProperties#maxSizeBytes`、V9 `chk_lesson_upload_size`、`apps/admin` `MAX_VIDEO_BYTES` | `apps/admin` 以 `2_147_483_648` 镜像 `lp.course.upload.max-size-bytes`，只用于上传前快速失败；权威判定仍只有服务端一处，两侧靠注释约束同步 | 技术债：将来应由服务端下发（会话响应或配置接口返回上限），前端不再持有常量。现阶段可接受，因为客户端只做快速失败、服务端才是权威 | 2026-11-10 |
| 教师端入口已可用，缺口收窄为入驻前提 | 1c-3b-2；`apps/admin/src/router.ts` | 教师已可登录 `apps/admin`，入口为「课程管理」（`/teacher-courses`，建课与拟定价）与「课时视频」（`/lesson-videos`，章节/课时新增与编辑、上传与预览）；准入、守卫与菜单由前端 `router.ts` 的纯函数 `canAccess` / `defaultRouteFor` / `visibleMenu` 驱动，两个教师页守卫放 ADMIN+TEACHER、菜单项只给 TEACHER，双角色落地 `/`；数据边界仍在后端：`/api/v1/admin/**` 对 TEACHER 仍返回 `40301`。仍需注意：纯 TEACHER 必须有 `teacher_profile` 才有数据，否则教师接口 403、页面提示「当前账号尚未完成教师入驻」。播放预览已于 2026-11-10 补齐（`play-url` 签名 GET + 页面预览播放器）。当时验证用的本地夹具未进仓库 | 剩余缺口是转码与教师独立工作台；登录准入与播放预览本身已不再是缺口 | 2026-11-10 |
| 支付重付约束 | SPEC-TRADE-004 AC-3、AC-4；engineering-plan 2.3 | 重新发起支付时直接复用未关闭的支付单，未返回 62006；`ErrorCode` 中没有 62006，也不要求先关闭原单 | 未排期；属支付发起与重付的业务缺口（L2-2），不阻塞 L1 上线 | 2026-11-10 |
| 全文搜索与多维筛选 | product-lifecycle F02；engineering-plan 1.3 | 仅 `LIKE` 标题/副标题；无难度、热度、上新排序；`ft_` 索引未被查询使用 | 未排期 | 2026-11-09 |
| 资料维护与账号注销 | product-lifecycle F01；engineering-plan 15.2 D8；SPEC-PLAT-004 | 资料修改未实现；注销已有 `POST/DELETE/GET /api/v1/auth/account-cancellation`（冷静期 7 天、重复申请幂等、阻塞返回 67003、依赖失败返回 50401），trade 冷静期守卫已实现（下单与支付返回 67005、200ms 探测、45s 缓存、提交后异步主动失效）；执行任务与匿名化已由定时任务自动执行，告警规则见 `infra/observability/prometheus-rules.yml`（3 条，已由本地监控栈加载求值；AC-33 的 firing 已由 `scripts/observability-ac33-drill.sh` 跑通）；Web `/account` 与小程序账号页已提供注销申请、冷静期倒计时、撤销与阻塞提示，Web 侧 2026-11-10 用真实账号实跑验证（截图与请求响应原文在仓库外 `/Users/caspian/lesson-platform-evidence/ac22-20261110/`），小程序真机预览尚未验证（AppID 已提供并注入本机、未入库，见 §4.3） | 进行中（SPEC-PLAT-004 32/33，仅剩 AC-22 外部阻塞，见 §4.3） | 2026-11-10 |
| 游客试看免费课时 | product-lifecycle 6.3、6.8 播放权限、F03；SPEC-LEARN-003 | 需求侧：product-lifecycle 6.3 允许未登录游客浏览课程详情并试看免费课时，6.8 的口径是「免费课时所有用户可看」。实现侧：网关 `lp.identity.optional-auth` 只含 `/api/v1/courses/**`、`/api/v1/categories/**` 与 `/api/v1/orders/purchase-ticket/redeem`，`/api/v1/lessons/**` 不在其中，匿名请求在网关即返回 `40101`；即使放行，`LessonController#playToken` 在缺少 `X-User-Id` 时同样抛 `40101`。规格侧：SPEC-LEARN-003 的 AC-1 至 AC-6 全部以已登录用户为前提，没有匿名试看的 AC | 未排期；需先冻结匿名试看口径（是否签发匿名凭证、频控、防盗链），再决定是否把播放接口加入 `optional-auth`；当前「免费课时可试看」只对已登录用户成立 | 2026-11-10 |
| 同一用户同一课时最多 2 路并发播放 | product-lifecycle 6.5 视频上传与播放约束；F03 | 仅产品承诺：SPEC-LEARN-003 没有并发路数的 AC；`lp-learn-service` 的 `play-token` 只校验登录与权益，不计数也不拒绝，同一用户可对同一课时无限次取签名并同时播放，无播放会话表或 Redis 占位 | 未排期；要么补 AC 并在签发或播放链路落地计数（Redis 租约或播放会话表），要么从产品口径中删除；当前既不实现也不校验 | 2026-11-10 |
| 注销后原用户名登录返回 `40101`，`67001` 只在匿名名上可达 | SPEC-PLAT-004 AC-13/AC-14 与 AC-15；`AuthServiceImpl.login`、`AccountAnonymizationMapper` | #150（`02a790b`）已按方案 A 落地「仅 `status=3` 先判状态、后验密码」，用 `deleted_{userId}` 登录返回 `67001`。但 AC-15 同时要求 `username` 改为不可逆占位，匿名化后原用户名不再存在，原用户名登录在查账号阶段即返回 `40101`；真实验证覆盖旧 token 下的 `/me`、`/refresh` 与匿名名登录的 `67001` | 已按方案 A 收敛，不再待裁决；演练脚本同时断言原用户名 `40101` 与匿名名 `67001`，并用「仅改 `status=3`、密码不变」的 fixture 证明状态门禁先于密码，禁用账号分支断言「密码优先、`40301` 在后」 | 2026-11-10 |
| 手动取消不释放优惠券 | SPEC-TRADE-006、SPEC-TRADE-007 AC-5 | 超时关单会释放订单占用的优惠券，手动取消（`OrderServiceImpl.cancel`）只关闭订单与支付单，券仍停留为已使用且绑定该订单，用户丢券且两种取消行为不一致 | 未排期；行为不一致 | 2026-11-09 |
| 幂等层降级时同键换 body 无法识别 | SPEC-TRADE-001 AC-5、SPEC-TRADE-008 | Redis 不可用时幂等层记录 WARN 日志并累加 `lp.trade.idempotency.degraded` 指标后放行，该窗口内同键换请求体不会被识别为 `40901`，仅靠数据库唯一键（`uk_idempotency`、`uk_active_order`、支付单行锁）兜底 | 已接受；依赖监控告警 | 2026-11-09 |
| 前端 `40901` 文案仍为「用户名已被占用」 | SPEC-TRADE-001 AC-5 | 下单与退款幂等冲突返回 `40901` 时，Web 端提示「用户名已被占用」，与「同一幂等键换请求体」语义不符，会误导用户；改文案需同步修改 `apps/web/src/utils/errors.spec.ts` 中对 `40901` 的断言 | 已知问题；非本 PR 引入 | 2026-11-09 |
| 端来源识别 | SPEC-PLAT-002 | 网关只能区分 `admin` 与 `web`，小程序请求被记为 `web`；`X-User-Source=miniapp` 的下单拦截不可达 | 未排期；L2-1 | 2026-11-09 |
| 后台评论、举报与教师管理 | product-lifecycle F14 | 后台已有课程审核、用户、订单、退款审核、教师入驻审核接口与看板 | 依赖 UGC 与界面 | 2026-11-09 |
| 小程序评论 | product-lifecycle 4.3 | 小程序无评论页面 | 依赖 UGC | 2026-11-09 |
| 小程序购买 ticket 闭环 | engineering-plan 15.2 D2；错误码 62012–62015 | ticket 签发与兑换接口已实现、Web 可兑换；小程序只复制普通链接，不签发 ticket | 前后端未接线 | 2026-11-09 |
| 监控栈与告警 | engineering-plan 13.6 M0-40/M0-41 | 8 个服务均含 `micrometer-registry-prometheus`，`/actuator/prometheus` 可抓取并有 `http_server_requests`；`infra/observability` 提供 Prometheus / Alertmanager / Grafana 编排，`rule_files` 直接挂载现有三条规则，本地 8/8 target UP、规则组 `health=ok` | 规则已真实求值；AC-33 的 firing 与恢复已由 `scripts/observability-ac33-drill.sh` 本地跑通（`increase(...[15m]) >= 3` + `for: 5m`，门槛与 5 分钟去抖未动，单批注入），Grafana 按边界不铺面板 | 2026-11-10 |
| 不存在的路径返回 `50001` | `lp-common` 的 `GlobalExceptionHandler` | `NoResourceFoundException` 落入兜底 `Exception` 分支，被报成 `50001 internal server error`；实际是路径或端点不存在（如未注册的 `/actuator/prometheus`），排查时容易被误导为服务端故障 | 技术债；应映射为 `40401`，与 `ErrorCode.NOT_FOUND` 对齐 | 2026-11-10 |
| 预签名地址与服务端回读地址未拆分 | `lp-course-service` 的 `lp.course.upload.minio-endpoint` | 同一个端点既用于生成浏览器侧预签名 PUT URL，又用于 confirm 时容器内回读对象：容器内必须指 `http://minio:9000`，而该主机名在宿主机浏览器里不可解析，本地靠修改 `/etc/hosts` 兜底；指宿主机 LAN IP 则容器内 `connection refused`（MinIO 只绑定 `127.0.0.1`） | 技术债；应拆成「浏览器侧签名地址」与「服务端回读地址」两个配置，拆分前本地按 `docs/local-dev-guide.md` 的 hosts 步骤操作 | 2026-11-10 |
| 播放签名地址使用容器内主机名，浏览器不可达 | SPEC-PLAT-006 AC-6；`lp.course.upload.minio-endpoint`、`LessonUploadUrlSigner#presignGet` | `play-url` 对 `lp.course.upload.minio-endpoint`（compose 默认 `http://minio:9000`）签名，宿主机浏览器无法解析 `minio`；本地验证只能靠 `/etc/hosts` 或 Chrome `--host-resolver-rules=MAP minio 127.0.0.1` 绕过，**未解决** | 生产必须改为公网 HTTPS 媒体域名签发（必要时前置 CDN），并拆分「浏览器侧签名地址」与「服务端回读地址」两个配置；与上方预签名地址行同源 | 2026-11-10 |
| 价格变更未独立成表 | `CourseInternalServiceImpl#review`、`course_review_audit.remark` | 审核审计表没有 detail 列，本片不加迁移，价格 before/after（分）连操作人与决策时间一起写进现有 `remark` 文本，形如 `priceFen 19900 -> 29900`；操作人与时间另有独立列 | 技术债：价格变更未独立成表，现挂在审核审计 remark 里，将来做价格看板时应拆表；本片不解决 | 2026-11-10 |
| 同一实体在两个接口使用不同单位 | `TeacherCourseRequest`（元）、`CourseReviewRequest#priceFen`（分） | 教师提交用元、运营审核确定最终价用分，换算全部在服务端完成、前端不得自行换算；两种表示并存提高了契约理解成本 | 技术债：同一实体在两个接口使用不同单位，长期应统一为分；本片不改 | 2026-11-10 |
| 退款对账结论字段曾用字符串存枚举 | `refund_request.reconcile_finding`；`RefundReconcileFinding` | 早期实现用 `VARCHAR(32)` 存字符串枚举，后按规范改为 `TINYINT` 结论码 + `RefundReconcileFinding` 常量类，旧分支已删除；实体 `RefundRequest.reconcileFinding` 现为 `Integer`，日志取可读文本走 `RefundReconcileFinding.nameOf(...)` | 教训：新增枚举字段按 `TINYINT` 结论码 + 常量类的形态，不要写字符串；该改动只留在 commit 历史里，故登记在此 | 2026-11-12 |
| 小程序 CI 上传 | engineering-plan 13.6 M0-49 | CI 仅做静态检查，无 `miniprogram-ci` 上传 | AppID 已提供（本机注入、未入库）；缺上传私钥与 CI secret | 2026-11-09 |
| staging/prod 部署 | engineering-plan 3.3、3.4、13.2 M5 | 镜像构建与推送已有：`.github/workflows/release-images.yml` 默认推 ghcr、支持 `platforms`（默认 `linux/amd64`），但只在 `v*` tag 与手动 `workflow_dispatch` 触发，**不监听 develop**，且无 deploy 作业，故 staging 需手动发布；仍无 K8s 清单与部署流水线 | 缺服务器、域名、备案与凭据 | 2026-11-11 |
| 业务服务不校验身份，只信任网关注入头 | engineering-plan 8.3、11.1；SPEC-PLAT-001、SPEC-PLAT-005 | **显式架构前提，不是疏漏**：8 个业务服务的 `/api/v1/**` 自身不做身份鉴权，全部依赖网关在 JWT 校验后注入 `X-User-Id` / `X-User-Roles` / `X-User-Permissions`；2026-11-11 本地演练直连 `127.0.0.1:8103` 伪造这组头得到 `200 code=0`，`scripts/security/authz-drill.sh` 将该断言保留为 FAIL，不改写为通过 | **部署前置（必须可验证，不接受「靠内网隔离」这种无法判定的说法）**：生产网络策略只允许网关访问各业务服务端口；部署后从非网关 Pod（或集群外节点）直连任一业务服务端口，必须超时或被拒绝，命令与输出记入发布单。**该前置只在 A+（单机 Compose 双网段）部署形态下部分成立**：A+ 能验证「外部主机直连被拒」（端口只绑 `127.0.0.1` + 防火墙）与「Web/Admin 容器直连被拒」（两者只接 frontend 网），但**后端网内任意容器仍可直连业务端口**，属残余风险，必须写进发布单，不得写成「已隔离」；「非网关 Pod 直连被拒」要等 K8s NetworkPolicy 落地后才能验证。网络策略缺失时该架构前提不成立 | 2026-11-11 |
| 服务间身份鉴权（内部 token 或 mTLS） | 上一条缺口；engineering-plan 第 8 章 | 未实现，本片不写代码，只登记方案：A. 网关转发时附加短时内部签名（含 audience 与来源），业务服务校验；B. 服务网格 mTLS + 网络策略；C. 只做网络策略、不加应用层鉴权 | 待决策。选型前置：确认部署形态是否提供 K8s 网络策略、是否存在服务间直调链路；决策后单开一片实现，并在鉴权契约变更前完成评审 | 2026-11-11 |
| 安全演练的验收数字不可在干净环境复现 | `#247`、`.github/workflows/security-ci.yml`；`docs/local-dev-guide.md` 7.1 | `#247` 记录的 `pass=22 fail=1` 是在有历史数据的开发机上跑出的：该库已有 `lp_oms.payment_reconcile` 行，且 `demo_teacher_lin` / `demo_teacher_su` 的 id 恰为 21/22。干净 schema 加 `demo-seed.sql` 下，演练在第一条断言之前就退出（`payment_reconcile` 为空，4 条 `ERROR 1064 ... near ''`），没有输出 `SUMMARY`；当时 workflow 的 `continue-on-error` 又让该步骤显示为绿。修复见 PR #254：导入账单夹具、按 username 联查教师、缺数据显式失败、无 `SUMMARY` 行时步骤判红 | 验收只认干净 runner 上 `security-ci` 的 `SUMMARY`；本机历史数据下的通过数不得作为证据。**阈值到期条件**：网络策略落地并验证直连业务端口被拒后，越权阈值由 `fail<=1` 降为 `fail=0`；届时仍 FAIL 即回归，不得再以「已登记缺口」解释 | 2026-11-11 |

---

# 5. 未列入计划但建议做

以下均为**建议**，不是既定计划；工作量级别为小/中/大。

| 建议 | 理由 | 工作量 | 最后核对日期 |
|---|---|---|---|
| 统一分页参数 | 课程列表用 `page/size`，订单与后台用 `pageNum/pageSize`，前端需分别适配，易出错 | 小 | 2026-11-09 |
| 接口契约文档或 OpenAPI | 前后端 TypeScript 类型手工维护，字段漂移只能靠评审发现 | 中 | 2026-11-09 |
| CI 内端到端冒烟 | `smoke.sh` 依赖本地栈，CI 只跑单测，合并前无法验证跨服务链路 | 中 | 2026-11-09 |
| 备份脚本接入定时与异地留存 | 演练脚本已验证备份可恢复，但仍是手动执行，dump 落本机 `logs/`，无异地副本与保留策略 | 中 | 2026-11-10 |
| 自建镜像漏洞扫描 | 依赖与基础镜像扫描已进 CI 基线（`supply-chain-ci.yml`），自建镜像要等 E2E 建栈后才能扫 | 小 | 2026-11-10 |
| 管理操作统一审计 | 已覆盖课程、退款、教师三类审核并支持按对象与按操作人查询；用户、订单、角色等管理操作仍无统一留痕 | 中 | 2026-11-10 |
| 日志聚合与告警通道 | 只有本地容器日志与指标端点，无集中检索与通知 | 中 | 2026-11-09 |
| 匿名试看 | 文档面向游客承诺免费试看，当前播放签名接口要求登录 | 小 | 2026-11-09 |

---

# 6. 待决策与外部阻塞

| 事项 | 影响 | 当前状态 | 最后核对日期 |
|---|---|---|---|
| 支付宝商户号、应用私钥、公钥证书、回调域名 | 真实支付、退款、对账全部无法联调 | 未申请 | 2026-11-09 |
| 支付宝沙箱应用 | 可先于正式商户号完成支付联调 | 未申请 | 2026-11-09 |
| 微信小程序 AppID/AppSecret | code 登录、真机预览、提审与上传均无法进行 | AppID 已由负责人提供并注入本机（未入库）；AppSecret 需由后端环境变量注入，状态未核实 | 2026-11-09 |
| 小程序主体认证与教育/知识付费类目资质 | 决定 M6/M7 能否提审，审核可能驳回重提 | 未办理 | 2026-11-09 |
| 服务器、域名、ICP 备案、HTTPS 证书 | 决定 staging/prod 能否部署 | 未办理 | 2026-11-09 |
| 短信服务商签名与模板 | 手机号验证码登录未实现，若启用需先办理 | 未办理 | 2026-11-09 |
| 客户自配模型 API key（BYO） | AI 四项能力无法验收 | 未提供 | 2026-11-09 |
| MinIO 归档镜像的替代方案 | 本地依赖可用，生产存储选型未定 | D14 标注"M1 收尾前评估" | 2026-11-09 |

---

# 7. 与现有文档不一致之处

| 文档表述 | 实际实现 | 处理建议 | 最后核对日期 |
|---|---|---|---|
| README "初版不包含退款与对账" | 退款闭环已合并（`1a56af8`、`b4be3b6`）；对账已合并 CSV 导入、重跑、人工标记与日结接口，自动拉账与日结页面未做 | 拆分表述：退款已做；对账已做 CSV 链路与日结接口，自动拉账与页面未做 | 2026-11-10 |
| engineering-plan 1.2 教师工作台位于 `apps/admin` | 教师建课/改价/提交审核/撤回页面已在 `/teacher-courses` 落地；仍无独立教师工作台与统计看板 | 标注为「基础课程页已做，数据看板未做」 | 2026-11-11 |
| engineering-plan 1.3 允许 MySQL 全文索引搜索 | 查询使用 `LIKE`，`ft_` 索引未使用 | 标注当前为 `LIKE`，全文检索待做 | 2026-11-09 |
| product-lifecycle 3.1 游客可试看 | 播放签名接口要求登录（`40101`） | 确认是否允许匿名试看，再改实现或文档 | 2026-11-09 |
| engineering-plan 13.6 M0-34 支付适配接口位于 `lp-common` | 支付抽象 `PaymentGateway` 在 `lp-trade-service`，`lp-common` 无 `pay` 包 | 修订文档指向实际位置 | 2026-11-09 |
| engineering-plan 3.4/13.6 Nacos dataId 配置下发 | 服务读取本地 `application.yml`，仓库无 Nacos 配置模板与初始化脚本 | 标注 Nacos 仅为本地 Compose 依赖，配置仍走本地文件 | 2026-11-09 |
| 附录 B 将 `teacher_profile` 归入 `lp_ums` | 建表脚本在 `db/migration/course/V3__catalog.sql`（`lp_cms`） | 统一归属描述 | 2026-11-09 |
| 附录 B 列出 `audit_log` | 迁移中无该表，仅 `course_review_audit` | 标注为未建 | 2026-11-09 |
| engineering-plan 13.6 M0-41 监控栈本地编排 | 已落 `infra/observability/docker-compose.yml`、`prometheus.yml`、`alertmanager.yml` | 已实现 | 2026-11-10 |

---

# 8. 核对记录与未核实项

- 核对日期：2026-11-09；基线提交：`37c7648`（`develop`）。
- 核对方式：阅读 `docs/engineering-plan.md`、`docs/product-lifecycle.md`、控制器与服务的实际代码、`db/migration/**`、`apps/**` 页面与路由、`scripts/**`、`.github/workflows/**`，并对照 `git log` 中已合并 PR。
- 未核实（依赖运行态或外部凭据，本次未实测）：
  - 真实支付宝下单、回调验签、退款与对账（无商户凭据）。
  - 微信 `jscode2session` 真实链路（默认关闭，需 AppID/AppSecret）。
  - 小程序真机预览、提审与发布（AppID 已提供并本机注入；缺类目资质与真机执行）。
  - Nacos 配置下发与动态刷新（仓库无配置模板，需运行时验证）。
  - 本地运行中的接口返回：本次核对期间另一会话正在占用本地栈，按约定未停止、未重启、未调用其服务，以上结论均来自代码与脚本静态核对。

## 4.3 外部阻塞

以下条目不是技术债，也不是「已完成」：能力依赖仓库外部条件，条件到位前无法验证，不得在任何文档里写成已通过。

| 条目 | 文档出处 | 现状 | 卡点 | 条件到位后要补什么 | 最后核对日期 |
|---|---|---|---|---|---|
| 外部阻塞：小程序真机预览需真机执行与上传私钥 | SPEC-PLAT-004 AC-22；product-lifecycle F01 | `apps/miniapp/pages/account` 已实现注销入口、冷静期倒计时与撤销按钮，只有静态检查与 `node --check`；AppID 已由负责人提供并注入本机（git-ignored 的 `project.private.config.json`，未入库），`check.mjs --require-appid` 已通过，但尚无微信开发者工具或真机上的行为证据 | 缺上传私钥与 CI secret，无法上传或创建体验版；真机执行（P1–P5）尚未开始；备案未完成，提审仍被阻塞 | ① 用已注入的 AppID 导入 `apps/miniapp` 并在真机打开账号注销页；② 实跑申请、倒计时显示、撤销三个动作；③ 用有未完成订单或权益的账号验证 67003 阻塞提示常驻；④ 有支付环境时再验 67005 的下单与支付拦截；⑤ 把截图与接口响应原文补进本条与 L1-8，再把 AC-22 从「部分」改为「满足」 | 2026-11-13 |

AC-22 的 Web 侧已于 2026-11-10 用真实账号在浏览器跑通：申请 → 冷静期倒计时 → 撤销 → 阻塞账号的 67003 常驻提示 → 67005 的下单与支付门禁，证据在仓库外 `/Users/caspian/lesson-platform-evidence/ac22-20261110/`（截图与请求响应原文）。这条只覆盖 Web 端；小程序端同一能力仍是「代码已写、未真机验证」，因此 AC-22 保持「部分」，L1-8 记为 32/33。
