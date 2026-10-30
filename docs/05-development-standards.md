# my-lesson 开发规范与约束

> 版本：1.0.0 ｜ 最后更新：2026-10-27 ｜ 本文档面向所有参与 my-lesson 开发的人员，是硬性约束。

## 1. 工程结构规范

### 1.1 后端

- 工程为 Maven 多模块聚合，父工程 `my-lesson`（packaging=pom）统一管理依赖版本（`dependencyManagement`）。
- 新业务服务模块命名 `ml-{领域}`（如 `ml-user`），公共代码一律放入 `ml-common`。
- 每个业务服务内部按标准分层组织包结构（见第 2 节）。
- 启动类：`{模块}App.java`，位于 `org.example` 根包，标注 `@SpringBootApplication`、`@MapperScan("org.example.mapper")`、`@EnableDiscoveryClient`。

### 1.2 前端

- C 端 `ml-web`：`src/api`（接口封装）、`src/views`（页面）、`src/router`、`src/stores`、`src/types`、`src/layouts`。
- 管理端 `ml-web-admin`：`src/api`（按 ums/cms/sms/oms 分组）、`src/views`（按业务域分组）、`src/request`（请求封装）、`src/components`（通用组件）。
- 页面文件命名：`Xxx.vue`；管理端按 `XxxInsert.vue / XxxUpdate.vue / Xxx.vue`（列表）三件套组织。

## 2. 后端分层规范

每个业务服务必须遵循以下包结构（由代码生成器自动生成）：

```
org.example
├── controller        # 控制层：仅参数接收与结果返回，不写业务逻辑
├── dto               # 请求数据传输对象（Insert/Page 等）
├── vo                # 视图对象（响应给前端的聚合结构）
├── entity            # 实体类（对应数据库表）
│   └── table         # 实体 TableDef 辅助类（MyBatis-Flex 生成）
├── mapper            # 数据访问层（继承 BaseMapper）
├── service           # 业务接口
│   └── impl          # 业务实现（继承 ServiceImpl）
├── feign             # 远程调用接口（FeignClient）
├── mq                # 消息队列消费者
└── util / config     # 工具/配置
```

**分层职责约束**：

| 层 | 允许 | 禁止 |
|---|---|---|
| controller | 参数校验注解、调用 service、DTO 转换 | 直接操作 mapper、写业务逻辑 |
| service/impl | 全部业务逻辑、事务、Feign/MQ/Redis 调用 | 直接拼 SQL（使用 MyBatis-Flex QueryChain） |
| mapper | 继承 `BaseMapper<T>`，复杂查询用 QueryChain/UpdateChain | 编写 XML 映射文件（除非必需） |

## 3. 命名规范

| 对象 | 规范 | 示例 |
|---|---|---|
| 模块 | `ml-{领域}` | ml-user |
| 包名 | 全小写 | org.example.service.impl |
| 类名 | 大驼峰 | CourseServiceImpl |
| Controller 类 | `{实体}Controller` | CourseController |
| Service 接口 | `{实体}Service`，实现 `{实体}ServiceImpl` | SeckillService / SeckillServiceImpl |
| Mapper | `{实体}Mapper` | OrderMapper |
| DTO | 按用途后缀：`{实体}InsertDTO`、`{实体}PageDTO`、业务名 DTO | CourseInsertDTO / KillDTO |
| VO | `{实体}VO` 或业务名 VO | LoginVO |
| 方法名 | 小驼峰、动词短语 | loginByAccount、getByCode |
| 接口路径 | 小写，`api/v1/{资源}`，动作见第 4.1 节 | api/v1/course/page |
| 常量 | 全大写 + 下划线，集中放 `ML` 类 | SECKILL_COURSE_COUNT_PREFIX |
| 数据库字段 | 小写下划线 | fk_course_id |

## 4. 接口规范

### 4.1 RESTful 动作约定

| 动作 | 方法 | 示例 | 说明 |
|---|---|---|---|
| 新增 | POST | `/course/save` | — |
| 删除 | DELETE | `/course/remove/{id}` | — |
| 修改 | PUT | `/course/update` | — |
| 查询列表 | GET | `/course/list` | 全量 |
| 分页 | GET | `/course/page` | 参数走 Body（PageDTO） |
| 详情 | GET | `/course/getInfo/{id}` | — |
| 业务动作 | 语义化子路径 | `/seckill/kill`、`/user/loginByAccount` | — |

### 4.2 统一返回

- Controller 方法**不要**手动封装 `Result`，返回业务类型即可（`ResultAdvice` 自动包装；返回类型已是 `Result` 的除外）。
- 业务失败一律抛 `ServiceException(ResultCode.xxx, "提示文案")`，由 `ExceptionAdvice` 统一转为 Result 响应。
- 新增错误码时优先复用 `ResultCode` 既有分类（2000 服务端 / 3000 参数 / 4000 重复 / 5000 不存在 / 7000 友好提示），不重复造码。

### 4.3 参数校验

- 需要校验的接口：Controller 方法参数加 `@Validated`，DTO 字段使用 `@NotEmpty/@NotNull/@Pattern/@Size` 等注解，正则统一引用 `ML.Regex` 常量。
- 校验失败统一返回 `3000`，`coderMessage` 为第一条错误信息。
- 前端请求必须携带 `token` 请求头（网关强制，白名单除外）。

## 5. 异常处理约定

| 异常 | 处理位置 | 结果 |
|---|---|---|
| ServiceException | 业务层主动抛出 | 返回对应 ResultCode |
| MethodArgumentNotValidException / BindException | ExceptionAdvice | 返回 3000 + 首个字段错误 |
| 其他 Exception | ExceptionAdvice 兜底 | 返回 2000 服务器异常 + 打印堆栈 |
| Feign 调用失败 | 调用方自行处理 | 抛 6000 远程调用失败 |

**ServiceAspect 切面约束**（`ml-common`，对 `org.example.service.impl.*` 所有 public 方法生效）：

- 前置：方法参数存在 null 即抛 `3000 请求参数错误`（**因此业务方法参数不得传 null**；若某方法确实允许 null 入参，需在切面白名单处理或调整切点）。
- 后置：记录业务方法名、参数、返回值的日志。

## 6. 服务间调用规范（Feign）

- 接口统一放各自模块 `feign` 包，标注 `@FeignClient(name = "目标服务名", path = "api/v1/资源")`。
- 远程接口超时：连接超时 2000ms（`feign.client.config.*` 配置）。
- 远程调用返回值统一包一层 `Result<T>` 解析（`.getData()`），判空后再使用。
- 新增 Feign 接口后需在目标服务确认该接口存在且已注册（Nacos）。
- 错误码 `6000` 保留给"远程调用失败"，业务层勿复用。

## 7. 消息队列约定（RocketMQ）

| 项 | 约定值 |
|---|---|
| Topic | `ml-topic` |
| Tag | `ml-tag` |
| 生产者 | ml-sale（`RocketMQTemplate.convertAndSend("ml-topic:ml-tag", message)`） |
| 消费者 | ml-order `OrderMessageListener`，消费组 `ml-consumer-group`，集群模式，并发消费 |
| 消息体 | `ml-common/dto/OrderMessage`（fkUserId/fkCourseId/price/skPrice），必须可序列化 |
| NameServer | `192.168.137.77:9876` |

**约束**：生产/消费的消息体必须放在 `ml-common`（两端共享）；消息字段变更需同时修改生产与消费端并保证兼容。

## 8. Redis 规范（ML.Redis 常量）

所有 Key 必须在 `org.example.constant.ML.Redis` 中统一定义，禁止散落魔法字符串：

| 常量 | 格式 | 用途 |
|---|---|---|
| LOGIN_VCODE_PREFIX | `login_vcode:{phone}` | 登录验证码 |
| UNBOUND_VCODE_PREFIX | `unbound_vcode:{phone}` | 解绑验证码 |
| BOUND_VCODE_PREFIX | `bound_vcode:{phone}` | 绑定验证码 |
| USER_STATISTICS_DATA_KEY | `user_statistics_data` | 用户统计 |
| ORDER_STATISTICS_DATA_KEY | `order_statistics_data` | 订单统计 |
| TOP_NOTICE_KEY_PREFIX | `top_notice:{n}` | 置顶通知缓存 |
| TOP_ARTICLE_KEY_PREFIX | `top_article:{n}` | 置顶新闻缓存 |
| TOP_BANNER_KEY_PREFIX | `top_banner:{n}` | 置顶横幅缓存 |
| SECKILL_COURSE_INFO_PREFIX | `seckill:course_info:{courseId}` | 秒杀课程信息缓存 |
| SECKILL_COURSE_COUNT_PREFIX | `seckill:course_count:{courseId}` | 秒杀库存（预扣） |

其他 Key（登录 Token、秒杀成功标记 `SK_SUC:{seckillId}:{courseId}:{userId}`）也应遵循"业务:语义:{参数}"的统一命名风格。

操作封装统一走 `ml-common` 的 `MyRedis` 组件（string/hash/list/set/zset/geo 各类 ops 已封装）。

## 9. MinIO 规范（ML.MinIO 常量）

| 项 | 约定值 |
|---|---|
| 端点 | `http://192.168.137.77:9001`（`MinioUtil` 硬编码，换环境需修改） |
| 账号 | minioadmin / minioadmin |
| 桶 | `mylesson` |
| 目录 | `avatar`（头像）、`course-cover`（课程封面）、`course-summary`（摘要图）、`episode-video-cover`（视频封面）、`episode-video`（视频）、`banner`（横幅） |
| 默认资源 | 头像 12 生肖图、`default-course-cover.jpg`、`default-episode-video.mp4` 等，见 `ML.Course/Episode/Banner` 常量 |

上传类接口：Controller 接收 `MultipartFile`，调用 `MinioUtil.upload(file, dir, BUCKET)`，返回文件名；存储 DB 的字段（cover/video 等）为 MinIO 访问地址。

## 10. 数据库开发约束

1. **逻辑删除**：所有表必须有 `deleted` 字段，删除用逻辑删除，禁止物理 DELETE（代码生成器自动配置 `logicDeleteColumn=deleted`）。
2. **乐观锁**：所有表必须有 `version` 字段，更新由 MyBatis-Flex 乐观锁机制保障。
3. **公共字段**：`id/version/deleted/created/updated` 为公共字段，新表必须包含。
4. **跨库引用**：`fk_*` 外键字段 + 冗余展示字段（如 `course_title/course_cover/course_price`、`username`），**禁止跨库 JOIN**，一致性由业务代码保证。
5. **金额**：`decimal(8,2)`；状态枚举 `tinyint`，取值见 `ML` 常量。
6. **数据库权限**：按服务拆分库（ums/cms/sms/oms），新增业务先确定归属库，不跨库建表。

## 11. 代码生成约束（ml-generator）

- 新增表后：在 `GenUtil` 确认数据库连接 → 在 `Main` 放开对应模块生成方法 → 运行生成。
- 生成器会**覆盖已存在文件**（`overwriteEnable=true`），运行前确认无未提交的手工改动。
- 生成后必须人工检查：InsertDTO 是否已加校验注解、Controller 是否需要补 `@Validated`、业务接口是否需扩展。

## 12. 安全约束

| 项 | 要求 |
|---|---|
| 密码 | BCrypt 加密存储，禁止明文；修改密码校验旧密码（3003） |
| Token | 请求头 `token`，Redis 存储，30 分钟续期；网关统一校验 |
| 敏感字段 | 身份证号/手机号/邮箱有唯一性校验（4002/4003/4004） |
| 参数校验 | 用户输入必须走 `@Validated` + 正则（`ML.Regex`） |
| 上传 | 只经 MinIO 上传接口，文件名服务端生成（UUID），防路径穿越 |
| 支付 | 回调接口 `prePayNotify` 需校验来源合法性（支付宝验签），上线前补齐 |
| 接口文档 | Knife4j 文档地址在生产环境应关闭或加访问控制 |

## 13. Git 与工程规范

Git 与协作规则（分支模型、命名、提交信息、PR 评审、合并、tag 与发布、回滚、密钥禁止入库、AI Agent 约束、例外审批）一律以仓库根目录 `AGENTS.md` 为准，本节不再重复 AGENTS.md 已定义条款。

与本项目直接相关的两点：三套环境 `dev / staging / prod`；发布顺序为先后端 + Web、后微信小程序，至少错开 7 天。

## 14. 已知问题与改进建议（维护清单）

| 级别 | 问题 | 建议 |
|---|---|---|
| 高 | `ml-order` 数据源指向 `ml_sms`（应为 `ml_oms`） | 修正 bootstrap.yaml |
| 高 | `user_coupons` 表建表语句缺失 | 补充建表 SQL（见数据库文档 8.1） |
| 高 | 前端调用不存在的 `/course/search` 接口 | 后端补充搜索接口或前端改用 page |
| 中 | 秒杀全局锁 `skLock`，未按课程分片 | 使用 `SK_LOCK:COURSE:{courseId}` 分锁 |
| 中 | `UserCoupons` 关联注解 `selfField="fkCouponId"` 拼写与字段不符 | 修正为 `fkCouponsId` |
| 中 | 无单元测试（pom 引入 JUnit 但无测试类） | 为核心 Service/秒杀链路补测试 |
| 低 | `ServiceAspect` 拒绝 null 参数，误伤合法场景 | 切点细化或参数校验前置化 |
| 低 | `CommentController.deleteByUserId` 注解名与形参名不一致（`@PathVariable("userId") Long id`） | 统一命名 |
| 低 | 网关端口/路由完全依赖 Nacos | 仓库内补充默认配置便于离线启动 |
| 待办 | ml-chat、ml-barrage 空壳 | 按需实现聊天/弹幕业务 |
| 待办 | XXL-Job、EasyExcel、支付宝 SDK 依赖已引入未使用 | 按业务规划启用 |
