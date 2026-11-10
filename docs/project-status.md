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
| `develop` | `08c7509` | 2026-11-10 | PR #135 网关 CORS 白名单；同批合入 #132 可信客户端 IP 解析、#133 小程序登录频控、#134 网关限流、#136 让堆叠 PR 也触发必需检查 |
| `main` | `7364af7` | 2026-11-09 | PR #97，与开发线尚未对齐，下次发布合并时同步 |
| `v0.1.0` | 2026-11-06 | — | 首个可演示版本标签，指向退款闭环完成后的提交 |

## 2.2 里程碑状态

| 里程碑 | 状态 | 判定依据 | 最后核对日期 |
|---|---|---|---|
| M0 基建 | 完成（监控栈除外） | Compose、Flyway、网关、统一返回、身份头、CI、本地验收脚本均可运行；`infra/observability` 未建立 | 2026-11-09 |
| M1 账号与内容（含教师端） | 部分完成 | 注册登录、RBAC 种子、分类/课程/章节课时、教师课程 API、课程审核与审计、教师入驻审核接口已通；视频上传、全文搜索、教师前端未做 | 2026-11-09 |
| M2 交易与学习 | 部分完成 | 购物车 API、下单幂等、0 元课、模拟支付、权益 outbox、播放签名、进度回写已通；真实支付宝未接 | 2026-11-09 |
| M3 互动与后台 | 部分完成 | 运营审核、用户/订单查询、退款审核、看板概览已通；UGC、对账、举报处置未做 | 2026-11-09 |
| M4 AI MVP | 未开始 | `lp-ai-service` 仅有启动类与 `package-info` | 2026-11-09 |
| M5 Web 加固与上线 | 未开始 | 无压测、安全测试、灰度与部署流水线 | 2026-11-09 |
| M6 小程序端 | 部分完成 | 4 个页面 + 静默登录 + 购买引导；无评论，未配置 AppID，未提审 | 2026-11-09 |
| M7 小程序审核与灰度 | 未开始 | 无提审与发布记录 | 2026-11-09 |

## 2.3 阶段结论

当前处于 **M2 后端链路可演示、M3 刚起步** 的阶段。可演示的闭环是：教师通过 API 建课并提交 → 运营在后台审核定价上架 → 学员在 Web 下单 → 模拟支付回调 → 权益发放 → 播放签名 → 进度回写 → 学员发起退款申请 → 运营后台审核通过 → 模拟渠道退款成功并回收权益。

该闭环成立的前提是**本地模拟支付**（`LP_PAY_MOCK_ENABLED=true`），并且教师建课与教师入驻审核目前只有后端接口、没有界面。真实支付宝、UGC、AI、教师工作台界面、小程序评论、监控与部署流水线均未完成，详见第 4 章。

## 2.4 规格条目索引（条目 → 实现状态）

状态与层级取自对应域文件；AC 完成度按 `develop` `b22ab7c` 的代码与页面行为在 2026-11-09 逐条核对，此后新增条目在各自 PR 分支核对，日期记在对应规格条目。格式为「已满足 AC 数 / 该条目 AC 总数」。`进行中` 但 AC 已全满足的条目表示仍有非 AC 事项（端到端接线或界面）未完成。

| 规格条目 | 能力 | 状态 | 层级 | AC 完成度 |
|---|---|---|---|---|
| SPEC-COURSE-001 | 教师建课与课程归属 | 已完成 | — | 4/4 |
| SPEC-COURSE-002 | 可编辑范围与改稿回退 | 已完成 | — | 3/3 |
| SPEC-COURSE-003 | 提交审核 | 已完成 | — | 4/4 |
| SPEC-COURSE-004 | 运营审核、定价与首次上架 | 已完成 | — | 5/5 |
| SPEC-COURSE-005 | 教师下架与再上架 | 未开始 | L2-3 | 0/4 |
| SPEC-COURSE-006 | 运营强制下架 | 未开始 | L2-5 | 0/4 |
| SPEC-COURSE-007 | 公开展示与访问控制 | 已完成 | — | 4/4 |
| SPEC-COURSE-008 | 内容结构与删除限制 | 进行中 | L2-3 | 2/4 |
| SPEC-COURSE-009 | 教师入驻审核 | 已完成 | — | 5/5 |
| SPEC-COURSE-010 | 展示字段与长度约束 | 已完成 | — | 5/5 |
| SPEC-COURSE-011 | 定价口径 | 进行中 | L1-2 | 4/5 |
| SPEC-TRADE-001 | 下单与幂等 | 已完成 | — | 7/7 |
| SPEC-TRADE-002 | 0 元订单 | 已完成 | — | 5/5 |
| SPEC-TRADE-003 | 优惠券校验与核销 | 已完成 | — | 4/4 |
| SPEC-TRADE-004 | 支付发起与重付约束 | 进行中 | L2-2 | 5/7 |
| SPEC-TRADE-005 | 支付回调与三态处理 | 进行中 | — | 5/6 |
| SPEC-TRADE-006 | 订单取消 | 已完成 | — | 4/4 |
| SPEC-TRADE-007 | 超时关单 | 已完成 | L1-3 | 5/5 |
| SPEC-TRADE-008 | 退款准入 | 已完成 | — | 6/6 |
| SPEC-TRADE-009 | 退款审核与状态机 | 已完成 | — | 7/7 |
| SPEC-TRADE-010 | 退款失败与补偿 | 已完成 | — | 3/3 |
| SPEC-TRADE-011 | 退款成功后的权益处理 | 已完成 | — | 4/4 |
| SPEC-TRADE-012 | 购票（Web 购买、小程序引导） | 进行中 | L2-2 | 4/4 |
| SPEC-TRADE-013 | 金额单位与边界约束 | 已完成 | — | 5/5 |
| SPEC-TRADE-014 | 支付对账与差异处置 | 未开始 | 真实收款前置 | 0/4 |
| SPEC-LEARN-001 | 权益发放与幂等 | 已完成 | — | 4/4 |
| SPEC-LEARN-002 | 退款后的权益撤销 | 已完成 | — | 4/4 |
| SPEC-LEARN-003 | 播放凭证与访问控制 | 已完成 | — | 6/6 |
| SPEC-LEARN-004 | 进度上报与有效观看时长 | 已完成 | — | 6/6 |
| SPEC-LEARN-005 | 完课判定（双 90%） | 已完成 | — | 6/6 |
| SPEC-LEARN-006 | 我的课程列表 | 已完成 | — | 5/5 |
| SPEC-LEARN-007 | 课程进度查询 | 已完成 | — | 4/4 |
| SPEC-LEARN-008 | 退款准入进度汇总 | 已完成 | — | 5/5 |
| SPEC-PLAT-001 | 网关身份头剥离与注入 | 已完成 | — | 4/4 |
| SPEC-PLAT-002 | 端来源识别 | 未开始 | L2-1 | 0/4 |
| SPEC-PLAT-003 | 小程序登录与凭证防重放 | 已完成 | — | 5/5 |
| SPEC-PLAT-004 | 账号注销冷静期 | 进行中 | L1-8 | 0/27 |
| SPEC-PLAT-005 | 服务间内部调用鉴权 | 已完成 | — | 4/4 |
| SPEC-PLAT-006 | 视频上传校验与交付 | 未开始 | L1-11 | 0/6 |
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
| L1-2 | 金额边界与勾稽 | SPEC-TRADE-013（5/5）、SPEC-COURSE-011（4/5） | 进行中 | 金额单位与订单/支付/退款三处勾稽已完成；定价口径缺教师端提交入口（AC-1），见 4.2 | 2026-11-10 |
| L1-3 | 订单超时关单 | SPEC-TRADE-007（5/5） | 已完成 | L1-3 层级已从 SPEC-TRADE-004 移到实际承载它的 SPEC-TRADE-007；004 剩 2 条 AC 属「支付发起与重付」业务缺口，层级改回 L2-2，见 4.2 | 2026-11-10 |
| L1-4 | 幂等契约 | SPEC-TRADE-001（7/7） | 已完成 | `Idempotency-Key`、最终响应原文缓存、40901 与 40902 已实现；Redis 降级窗口的同键换体限制见 4.2 | 2026-11-10 |
| L1-5 | 教师入驻审核 | SPEC-COURSE-009（5/5） | 已完成 | 审核接口、权限码 `admin:teacher:audit` 与重新提交已通；运营与教师端无页面，档案未打 L1-5 层级标记 | 2026-11-10 |
| L1-6 | 登录风控与网关边界 | SPEC-PLAT-011（8/8）、SPEC-PLAT-012（8/8）、SPEC-PLAT-013（7/7） | 已完成 | 小程序登录频控、网关限流与 CORS 白名单三项 AC 全满足 | 2026-11-10 |
| L1-7 | 统一操作审计 | SPEC-PLAT-010（10/10） | 已完成 | 三类审核的统一审计已完成；原同层级的账号注销已按门槛编号移到 L1-8 | 2026-11-10 |
| L1-8 | 账号注销 | SPEC-PLAT-004（0/27） | 进行中 | 2.4 与 `docs/specs/platform.md` 已把 SPEC-PLAT-004 改标 L1-8；视频上传已补为 L1-11 | 2026-11-10 |
| L1-9 | 支付对账 | SPEC-TRADE-014（0/4） | 未开始 | 「未完成对账能力不得开启真实收款」已写在 4.1「支付宝对账」行，并由 SPEC-TRADE-014 AC-4 约束；层级为「真实收款前置」 | 2026-11-10 |
| L1-10 | 发布前工程化验证 | 无对应规格条目 | 未开始 | E2E 冒烟进 CI、备份恢复演练、依赖与镜像漏洞扫描三项均未做，见 3.5 与第 5 章 | 2026-11-10 |
| L1-11 | 内容生产通路（上传→处理→播放链路，教师端或运营后台任一） | SPEC-PLAT-006（0/6） | 未开始 | 2026-11-10 补入，理由：缺少可用上传链路时课程内容无法生产 | 2026-11-10 |

---

# 3. 已做

## 3.1 后端与网关

| 能力 | 完成程度与限制 | 证据 | 最后核对日期 |
|---|---|---|---|
| 账号与 JWT | 注册、密码登录、刷新、登出、`/me` 可用；密码 BCrypt 存储；无资料修改与注销 | `lp-auth-service/.../controller/AuthController.java`、`db/migration/auth/V2__user_auth.sql` | 2026-11-09 |
| 小程序 code 登录 | 接口与真实 `jscode2session` 客户端均已实现，默认关闭；本地可用 mock 客户端；登录入口按真实 IP 与 `X-Device-Id` 双维度频控，Redis 故障放行并打点 | `AuthController.java`、`security/HttpWechatMiniappClient.java`、`security/MiniappLoginRateLimitFilter.java`、`config/MiniappProperties.java` | 2026-11-10 |
| 网关 | 7 条服务路由、身份头剥离与注入、JWT 三态校验，默认 `hard`；`/api/v1/**` 按用户或真实客户端 IP 做每实例内存限流（默认 300 次/分钟/实例，集群容量 = 阈值 × 副本数），超限返回 429 + `Retry-After` + 统一信封；CORS 走显式来源白名单，预检在鉴权前处理，非 dev 未配置来源时启动失败；`/internal/**` 与 `/actuator/**` 不经路由 | `lp-gateway/src/main/resources/application.yml`、`application-dev.yml`、`filter/HeaderSanitizeFilter.java`、`filter/JwtAuthFilter.java`、`filter/GatewayRateLimitFilter.java`、`config/CorsConfig.java` | 2026-11-10 |
| 课程浏览 | 已上架课程分页、分类、关键词（`LIKE` 标题/副标题）、详情、目录；下架课程对非持有者按 404 处理 | `lp-course-service/.../controller/CoursePublicController.java`、`mapper/CourseMapper.java` | 2026-11-09 |
| 教师课程 API | 建课、改课、提交审核、章节课时增删改、状态计数；按 `owner_teacher_id` 做归属校验；建课与改课在服务层校验教师入驻审核状态，未通过返回 `61006` 且不写课程 | `controller/TeacherCourseController.java`、`service/impl/TeacherCourseServiceImpl.java` | 2026-11-09 |
| 课程审核 | 待审队列、通过并定价上架、驳回、审核审计查询；审核动作同时写入统一审计表（含操作人角色、前后状态、traceId），与状态更新同事务 | `lp-admin-service/.../controller/AdminCourseController.java`、`db/migration/course/V4__course_review_audit.sql`、`db/migration/course/V7__admin_audit_log.sql` | 2026-11-10 |
| 教师入驻审核 | 审核字段与存量回填、按状态筛选的审核队列、通过/驳回（驳回原因必填）、审核留痕（操作人、时间、前后状态）、教师查询与重新提交；权限码 `admin:teacher:audit`；审核动作同时写入统一审计表并与状态更新同事务。**驳回原因超过 300 字被静默截断（见 4.2）** | `db/migration/course/V6__teacher_audit.sql`、`service/impl/TeacherAuditServiceImpl.java`、`lp-admin-service/.../controller/AdminTeacherController.java` | 2026-11-10 |
| 统一操作审计 | 三类审核动作（课程/退款/教师）写入 `lp_cms.admin_audit_log`；课程与教师审核同事务写入，退款审核经 `lp_oms.audit_outbox` 至少一次投递、`dedupe_key` 去重；查询接口 `GET /api/v1/admin/audit-logs` 支持按对象与按操作人+时间，权限码 `admin:audit:list`；`course_review_audit` 保留双写、历史数据不迁移 | `db/migration/course/V7__admin_audit_log.sql`、`db/migration/trade/V10__audit_outbox.sql`、`lp-course-service/.../service/AdminAuditService.java`、`lp-trade-service/.../audit/AuditOutboxRelay.java`、`lp-admin-service/.../controller/AdminAuditController.java` | 2026-11-10 |
| 交易 | 购物车 API、下单幂等、0 元课直接完成、订单列表/详情/取消、超时关单（30 分钟，条件更新与支付回调竞争安全，同时关闭待支付记录并释放优惠券）；优惠券在下单时校验并核销；下单/支付/退款统一 `Idempotency-Key`（Redis 占位与结果重放，同键不同体 40901，处理中超时 40902，成功结果缓存 24h、支付发起 30 分钟） | `lp-trade-service/.../controller/OrderController.java`、`service/impl/OrderServiceImpl.java`、`job/OrderTimeoutJob.java`、`idempotency/IdempotencyService.java` | 2026-11-09 |
| 支付 | 支付发起（`Idempotency-Key` 必填，同键并发复用首次预支付结果）、`/api/v1/payments/alipay/notify` 回调入口、金额与渠道校验、重复回调幂等；**真实渠道未配置时统一返回 50205**，本地由 mock 渠道替代 | `payment/MockPaymentGateway.java`、`payment/UnconfiguredPaymentGateway.java`、`controller/PaymentController.java`、`idempotency/IdempotencyService.java` | 2026-11-09 |
| 退款 | 准入校验（付款后 7 天内且学习进度 ≤ 20%，超限返回 `62007` 且不落单）、生成待审核单（不自动退款）、运营审核、驳回需原因并可再次申请、审核通过后原渠道退款、权益软失效、撤销 outbox；渠道明确拒绝落 4 并放回可申请，调用超时等结果未知落 5 且订单保持退款中，learn 进度不可用时失败关闭返回 `50201` | `service/impl/RefundServiceImpl.java`、`db/migration/trade/V7__refund_review.sql`、`db/migration/trade/V9__refund_unknown_result.sql`、`controller/RefundInternalController.java` | 2026-11-09 |
| 权益与 outbox | 本地消息表、定时中继、指数退避、死信、运营重放、Prometheus 指标 | `entitlement/EntitlementRelay.java`、`entitlement/OutboxAdminService.java`、`controller/InternalEntitlementOutboxController.java` | 2026-11-09 |
| 学习交付 | MinIO 预签名播放地址、免费课时试看、我的课程（含已退款标记）、进度上报与查询；**完课仅按有效观看 ≥ 90% 置位，缺播放位置 ≥ 90% 条件（D7 未完成）** | `lp-learn-service/.../controller/LessonController.java`、`service/impl/PlayTokenServiceImpl.java`、`service/impl/LearningProgressServiceImpl.java` | 2026-11-09 |
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
| 未做 | 教师入驻审核页面、课程/课时管理、评论与举报处置、角色与菜单管理、教师工作台界面 | `apps/admin/src/router.ts` 仅有 5 个业务页面 | 2026-11-09 |

## 3.4 微信小程序

| 能力 | 完成程度与限制 | 证据 | 最后核对日期 |
|---|---|---|---|
| 页面 | 首页、课程详情、我的课程、播放页 | `apps/miniapp/app.json` | 2026-11-09 |
| 登录 | `wx.login` 静默换取 token，失败时展示后端错误 | `apps/miniapp/utils/auth.js` | 2026-11-09 |
| 购买引导 | 课程页复制 Web 购买链接，提示到浏览器购买；**未调用购买 ticket 接口** | `apps/miniapp/pages/course/course.js` | 2026-11-09 |
| 未做 | 评论、正式 AppID 配置、真机验收、提审与发布、`miniprogram-ci` 上传 | `apps/miniapp/project.config.json`、`.github/workflows/frontend-ci.yml` | 2026-11-09 |

## 3.5 基础设施、脚本与 CI

| 能力 | 完成程度与限制 | 证据 | 最后核对日期 |
|---|---|---|---|
| 本地一键起停 | Compose 拉起 MySQL/Redis/RocketMQ/MinIO/Nacos、7 个后端服务、Web 与后台；构建 jar 与前端、执行迁移、导入种子 | `infra/docker-compose.yml`、`scripts/dev-up.sh`、`scripts/dev-down.sh` | 2026-11-09 |
| 演示数据 | 教师/运营/学员/管理员账号、多状态课程与一笔已支付订单 | `db/seed/demo-seed.sql`、`scripts/seed/seed-demo.sh` | 2026-11-09 |
| 验收与演练 | 冒烟、outbox 故障演练、退款闭环演练、一条命令验收 | `scripts/smoke.sh`、`scripts/outbox-fault-drill.sh`、`scripts/refund-drill.sh`、`scripts/verify-all.sh` | 2026-11-09 |
| CI | `backend-build`/`backend-test`、`frontend-check`、`miniapp-check`、`docs-check` | `.github/workflows/backend-ci.yml`、`frontend-ci.yml`、`docs-ci.yml` | 2026-11-09 |
| 本地钩子 | `pre-commit` 跑文档校验与 Spotless，`commit-msg` 跑提交信息校验 | `.githooks/pre-commit`、`.githooks/commit-msg`、`scripts/install-hooks.sh` | 2026-11-09 |
| 测试 | 47 个后端测试类、6 个前端 spec；无端到端测试进入 CI | `lp-*/src/test/**`、`apps/*/src/**/*.spec.ts` | 2026-11-09 |
| 未做 | 镜像构建与推送、staging/prod 部署、监控栈编排、告警规则、备份演练 | `.github/workflows/**`、`infra/**` | 2026-11-10 |

---

# 4. 计划了但未做

## 4.1 阻塞于商户凭据/渠道接入

以下条目不取决于开发排期：缺真实商户凭据或渠道接入，凭据到位前无法实现与验证。

| 功能 | 文档出处 | 现状 | 卡点 | 最后核对日期 |
|---|---|---|---|---|
| 真实支付宝支付 | engineering-plan 1.2、5.5、13.2 M2；15.2 D2 | 只有 `MockPaymentGateway` 与未配置即报 50205 的占位实现，无支付宝 SDK 适配器 | 缺商户号、应用私钥、公钥证书、回调域名 | 2026-11-09 |
| 支付宝对账 | engineering-plan 1.2、13.2 M3、15.1 R11；SPEC-TRADE-014 | 无 `payment_reconcile` 表、无拉账任务、无差异告警 | 依赖商户号与账单权限；**未完成对账能力不得开启真实收款** | 2026-11-09 |
| 支付宝回调 `app_id` 校验 | SPEC-TRADE-005 AC-6；engineering-plan 5.5、8.3 | 回调只验签与校验金额，未比对 `app_id`；当前为 Mock 渠道 | 阻塞：真实渠道接入时实现 | 2026-11-09 |

## 4.2 其他未做功能

| 功能 | 文档出处 | 现状 | 卡点 | 最后核对日期 |
|---|---|---|---|---|
| 评论、两级回复、收藏、举报与内容审核 | product-lifecycle F11/F12、4.3；engineering-plan 13.2 M3 | `lp-ugc-service` 仅有启动类与 `package-info`，`lp_ugc` 仅有 V1 基线 | 未排期；依赖 UGC 表设计与审核链路 | 2026-11-09 |
| AI 课程画像、审核、文案、学伴、BYO 模型配置 | product-lifecycle F15–F18；engineering-plan 第 7 章、13.2 M4 | `lp-ai-service` 仅有启动类与 `package-info`，`lp_ai` 仅有 V1 基线 | 未排期；缺客户模型 key 与配置界面 | 2026-11-09 |
| 购物车页面 | product-lifecycle F07、4.3 | 后端 `/api/v1/cart` 可用，Web 无购物车页面 | 未排期 | 2026-11-09 |
| 优惠券发券与领券 | product-lifecycle F07 | 只有 `coupon`/`user_coupon` 表与下单核销逻辑，无发券、领券、券列表接口 | 未排期；无运营配置入口 | 2026-11-09 |
| 教师入驻审核页面 | product-lifecycle F04；engineering-plan 13.2 M1 | 审核接口、权限码与教师重新提交接口已通；运营后台与教师端均无页面 | 未排期；依赖后台与教师工作台界面 | 2026-11-09 |
| 教师审核驳回原因超 300 字静默截断 | SPEC-PLAT-010、`AdminAuditService.record` | 驳回原因写入审计前按 300 字截断，接口不报错也不提示；超过 300 字时审计备注与实际驳回原因不一致 | 已知问题；处置方向：接口校验拒绝，超过 300 字返回 `40001` 并说明上限，不静默截断；不放宽 `remark` 字段 | 2026-11-10 |
| 教师工作台界面与数据看板 | engineering-plan 1.2、2.1；product-lifecycle F13；SPEC-COURSE-011 | 仅有课程 CRUD 接口；后台无教师页面，无销售/学习/评论统计接口 | 未排期；缺 `course_daily_stat` 等汇总表 | 2026-11-09 |
| 教师提交拟定价（前端入口） | SPEC-COURSE-011 AC-1 | 接口接受教师价格，运营审核可覆盖；后台无教师建课页面，教师无法实际提交拟定价 | 未排期；依赖教师工作台界面 | 2026-11-09 |
| 视频上传与 ffprobe 校验 | product-lifecycle F06；engineering-plan 15.2 D5 | 无上传接口；播放仅对已存在对象签名 | 未排期；存储与校验方案未落地 | 2026-11-09 |
| 支付重付约束 | SPEC-TRADE-004 AC-3、AC-4；engineering-plan 2.3 | 重新发起支付时直接复用未关闭的支付单，未返回 62006；`ErrorCode` 中没有 62006，也不要求先关闭原单 | 未排期；属支付发起与重付的业务缺口（L2-2），不阻塞 L1 上线 | 2026-11-10 |
| 全文搜索与多维筛选 | product-lifecycle F02；engineering-plan 1.3 | 仅 `LIKE` 标题/副标题；无难度、热度、上新排序；`ft_` 索引未被查询使用 | 未排期 | 2026-11-09 |
| 资料维护与账号注销 | product-lifecycle F01；engineering-plan 15.2 D8 | 只有 `GET /api/v1/auth/me`，无修改资料与注销流程 | 未排期 | 2026-11-09 |
| 手动取消不释放优惠券 | SPEC-TRADE-006、SPEC-TRADE-007 AC-5 | 超时关单会释放订单占用的优惠券，手动取消（`OrderServiceImpl.cancel`）只关闭订单与支付单，券仍停留为已使用且绑定该订单，用户丢券且两种取消行为不一致 | 未排期；行为不一致 | 2026-11-09 |
| 幂等层降级时同键换 body 无法识别 | SPEC-TRADE-001 AC-5、SPEC-TRADE-008 | Redis 不可用时幂等层记录 WARN 日志并累加 `lp.trade.idempotency.degraded` 指标后放行，该窗口内同键换请求体不会被识别为 `40901`，仅靠数据库唯一键（`uk_idempotency`、`uk_active_order`、支付单行锁）兜底 | 已接受；依赖监控告警 | 2026-11-09 |
| 前端 `40901` 文案仍为「用户名已被占用」 | SPEC-TRADE-001 AC-5 | 下单与退款幂等冲突返回 `40901` 时，Web 端提示「用户名已被占用」，与「同一幂等键换请求体」语义不符，会误导用户；改文案需同步修改 `apps/web/src/utils/errors.spec.ts` 中对 `40901` 的断言 | 已知问题；非本 PR 引入 | 2026-11-09 |
| 端来源识别 | SPEC-PLAT-002 | 网关只能区分 `admin` 与 `web`，小程序请求被记为 `web`；`X-User-Source=miniapp` 的下单拦截不可达 | 未排期；L2-1 | 2026-11-09 |
| 后台评论、举报与教师管理 | product-lifecycle F14 | 后台已有课程审核、用户、订单、退款审核、教师入驻审核接口与看板 | 依赖 UGC 与界面 | 2026-11-09 |
| 小程序评论 | product-lifecycle 4.3 | 小程序无评论页面 | 依赖 UGC | 2026-11-09 |
| 小程序购买 ticket 闭环 | engineering-plan 15.2 D2；错误码 62012–62015 | ticket 签发与兑换接口已实现、Web 可兑换；小程序只复制普通链接，不签发 ticket | 前后端未接线 | 2026-11-09 |
| 监控栈与告警 | engineering-plan 13.6 M0-40/M0-41 | 各服务暴露 `/actuator/prometheus`，trade 有 outbox 指标；无 Prometheus/Grafana 编排与告警规则 | 未排期 | 2026-11-09 |
| 小程序 CI 上传 | engineering-plan 13.6 M0-49 | CI 仅做静态检查，无 `miniprogram-ci` 上传 | 缺 AppID 与上传私钥 | 2026-11-09 |
| staging/prod 部署 | engineering-plan 3.3、3.4、13.2 M5 | 无镜像构建、K8s 清单或部署 workflow | 缺服务器、域名、备案与凭据 | 2026-11-09 |

---

# 5. 未列入计划但建议做

以下均为**建议**，不是既定计划；工作量级别为小/中/大。

| 建议 | 理由 | 工作量 | 最后核对日期 |
|---|---|---|---|
| 统一分页参数 | 课程列表用 `page/size`，订单与后台用 `pageNum/pageSize`，前端需分别适配，易出错 | 小 | 2026-11-09 |
| 接口契约文档或 OpenAPI | 前后端 TypeScript 类型手工维护，字段漂移只能靠评审发现 | 中 | 2026-11-09 |
| CI 内端到端冒烟 | `smoke.sh` 依赖本地栈，CI 只跑单测，合并前无法验证跨服务链路 | 中 | 2026-11-09 |
| 数据库备份与恢复演练脚本 | 目前只有迁移脚本，无备份、恢复与演练步骤 | 中 | 2026-11-09 |
| 依赖与镜像漏洞扫描 | CI 无依赖审计，镜像也不构建，供应链风险未覆盖 | 小 | 2026-11-09 |
| 管理操作统一审计 | 已覆盖课程、退款、教师三类审核并支持按对象与按操作人查询；用户、订单、角色等管理操作仍无统一留痕 | 中 | 2026-11-10 |
| MinIO 孤儿对象清理 | 课程/课时删除后对象仍占用存储，长期累积成本 | 小 | 2026-11-09 |
| 日志聚合与告警通道 | 只有本地容器日志与指标端点，无集中检索与通知 | 中 | 2026-11-09 |
| 匿名试看 | 文档面向游客承诺免费试看，当前播放签名接口要求登录 | 小 | 2026-11-09 |

---

# 6. 待决策与外部阻塞

| 事项 | 影响 | 当前状态 | 最后核对日期 |
|---|---|---|---|
| 支付宝商户号、应用私钥、公钥证书、回调域名 | 真实支付、退款、对账全部无法联调 | 未申请 | 2026-11-09 |
| 支付宝沙箱应用 | 可先于正式商户号完成支付联调 | 未申请 | 2026-11-09 |
| 微信小程序 AppID/AppSecret | code 登录、真机预览、提审与上传均无法进行 | 未申请 | 2026-11-09 |
| 小程序主体认证与教育/知识付费类目资质 | 决定 M6/M7 能否提审，审核可能驳回重提 | 未办理 | 2026-11-09 |
| 服务器、域名、ICP 备案、HTTPS 证书 | 决定 staging/prod 能否部署 | 未办理 | 2026-11-09 |
| 短信服务商签名与模板 | 手机号验证码登录未实现，若启用需先办理 | 未办理 | 2026-11-09 |
| 客户自配模型 API key（BYO） | AI 四项能力无法验收 | 未提供 | 2026-11-09 |
| MinIO 归档镜像的替代方案 | 本地依赖可用，生产存储选型未定 | D14 标注"M1 收尾前评估" | 2026-11-09 |

---

# 7. 与现有文档不一致之处

| 文档表述 | 实际实现 | 处理建议 | 最后核对日期 |
|---|---|---|---|
| README "初版不包含退款与对账" | 退款闭环已合并（`1a56af8`、`b4be3b6`），对账确实未做 | 拆分表述：退款已做、对账未做 | 2026-11-09 |
| engineering-plan 1.2 教师工作台位于 `apps/admin` | 后台仅有运营页面，无教师建课界面 | 标注为"仅 API，界面未做" | 2026-11-09 |
| engineering-plan 15.2 D11 下单 30 分钟自动关单 | 无定时关单任务 | 标注为未实现 | 2026-11-09 |
| engineering-plan 1.3 允许 MySQL 全文索引搜索 | 查询使用 `LIKE`，`ft_` 索引未使用 | 标注当前为 `LIKE`，全文检索待做 | 2026-11-09 |
| product-lifecycle 3.1 游客可试看 | 播放签名接口要求登录（`40101`） | 确认是否允许匿名试看，再改实现或文档 | 2026-11-09 |
| engineering-plan 13.6 M0-34 支付适配接口位于 `lp-common` | 支付抽象 `PaymentGateway` 在 `lp-trade-service`，`lp-common` 无 `pay` 包 | 修订文档指向实际位置 | 2026-11-09 |
| engineering-plan 3.4/13.6 Nacos dataId 配置下发 | 服务读取本地 `application.yml`，仓库无 Nacos 配置模板与初始化脚本 | 标注 Nacos 仅为本地 Compose 依赖，配置仍走本地文件 | 2026-11-09 |
| 附录 B 将 `teacher_profile` 归入 `lp_ums` | 建表脚本在 `db/migration/course/V3__catalog.sql`（`lp_cms`） | 统一归属描述 | 2026-11-09 |
| 附录 B 列出 `audit_log` | 迁移中无该表，仅 `course_review_audit` | 标注为未建 | 2026-11-09 |
| engineering-plan 13.6 M0-41 监控栈本地编排 | `infra/` 下无 observability 编排 | 标注为未实现 | 2026-11-09 |

---

# 8. 核对记录与未核实项

- 核对日期：2026-11-09；基线提交：`37c7648`（`develop`）。
- 核对方式：阅读 `docs/engineering-plan.md`、`docs/product-lifecycle.md`、控制器与服务的实际代码、`db/migration/**`、`apps/**` 页面与路由、`scripts/**`、`.github/workflows/**`，并对照 `git log` 中已合并 PR。
- 未核实（依赖运行态或外部凭据，本次未实测）：
  - 真实支付宝下单、回调验签、退款与对账（无商户凭据）。
  - 微信 `jscode2session` 真实链路（默认关闭，需 AppID/AppSecret）。
  - 小程序真机预览、提审与发布（无 AppID 与类目资质）。
  - Nacos 配置下发与动态刷新（仓库无配置模板，需运行时验证）。
  - 本地运行中的接口返回：本次核对期间另一会话正在占用本地栈，按约定未停止、未重启、未调用其服务，以上结论均来自代码与脚本静态核对。
