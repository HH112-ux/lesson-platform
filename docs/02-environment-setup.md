# my-lesson 环境搭建与快速启动

> 版本：1.0.0 ｜ 最后更新：2026-10-27

本文档说明从零搭建 my-lesson 开发/运行环境并启动全栈的完整步骤。

## 1. 环境要求

| 软件 | 版本要求 | 用途 |
|---|---|---|
| JDK | 17+（源码编译级别 17） | 后端运行 |
| Maven | 3.6+（使用 Maven 多模块聚合） | 后端构建 |
| Node.js | 18+ | 前端构建运行 |
| 包管理器 | npm（或 pnpm/yarn） | 前端依赖 |
| IDE | IntelliJ IDEA（推荐，工程含 `.idea` 配置） | 后端开发 |

## 2. 中间件清单

项目依赖以下中间件，均为**外部独立部署**，通过 Nacos 配置中心统一管理连接信息：

| 中间件 | 地址（示例环境） | 用途 | 备注 |
|---|---|---|---|
| MySQL | `192.168.137.77:3306` | 业务数据库（4 库 21 表） | 账号 `root`，密码见各模块数据源配置 |
| Nacos | `192.168.137.77:8848` | 注册中心 + 配置中心 | group=`ml-group` |
| Redis | 默认本地/配置中心下发 | Token、秒杀库存、缓存 | `common-config.yaml` 配置 |
| RocketMQ | `192.168.137.77:9876`（NameServer） | 秒杀异步下单 | topic=`ml-topic`，tag=`ml-tag` |
| MinIO | `http://192.168.137.77:9001` | 视频/图片对象存储 | 账号 `minioadmin` / `minioadmin`，桶 `mylesson` |

> ⚠️ 上述 IP 为项目当前硬编码/配置的示例地址（`192.168.137.77`），换环境时需同步修改，修改点见第 7 节。

## 3. 数据库初始化

数据库脚本位于 `ml-generator/SQL/` 目录，共 4 个脚本：

| 脚本 | 数据库 | 表数量 |
|---|---|---|
| `ml_ums.sql` | `ml_ums` | 5 张（user/role/menu/user_role/role_menu） |
| `ml_cms.sql` | `ml_cms` | 7 张（category/course/season/episode/comment/report/follow） |
| `ml_sms.sql` | `ml_sms` | 6 张（notice/banner/article/seckill/seckill_detail/coupons） |
| `ml_oms.sql` | `ml_oms` | 3 张（cart/order/order_detail） |

初始化步骤：

```bash
mysql -h <MySQL地址> -uroot -p < ml_ums.sql
mysql -h <MySQL地址> -uroot -p < ml_cms.sql
mysql -h <MySQL地址> -uroot -p < ml_sms.sql
mysql -h <MySQL地址> -uroot -p < ml_oms.sql
```

> 注意：`ml_sms` 库中 `coupons` 表仅定义优惠券模板；用户已领券记录存于 `user_coupons` 表（见《数据库设计》文档）。

## 4. Nacos 配置中心准备

所有服务通过 `bootstrap.yaml` 连接 Nacos 获取配置，统一约定：

- **服务地址**：`192.168.137.77:8848`
- **分组**：`ml-group`
- **文件扩展名**：`yaml`
- **共享配置**：`common-config.yaml`（group=ml-group，refresh=true）——存放 Redis、MinIO、MyBatis 等公共配置
- **各服务独立配置**：`ml-gateway.yaml` / `ml-user.yaml` / `ml-course.yaml` / `ml-sale.yaml` / `ml-order.yaml`（含网关端口 24101、路由、`token.white_list` 白名单等）

网关关键配置项（Nacos 侧）：

| 配置项 | 说明 |
|---|---|
| `server.port` | 网关端口（前端 baseURL 指向 `http://localhost:24101/`） |
| `spring.cloud.gateway.routes` | 路由表，按 `/course-server`、`/order-server`、`/user-server`、`/sale-server` 等前缀分发到各微服务 |
| `token.white_list` | 免鉴权白名单（从配置中心读取，`@RefreshScope` 动态刷新） |

## 5. 后端启动

### 5.1 构建

```bash
cd /Users/caspian/my-lesson
mvn clean install -DskipTests
```

### 5.2 启动顺序

必须先启动依赖的中间件与注册中心，再启动业务服务：

1. MySQL / Redis / RocketMQ / MinIO / Nacos
2. `ml-gateway`（GatewayApp）—— 网关
3. `ml-user`（UserApp，:24102）—— 用户服务
4. `ml-course`（CourseApp，:24103）—— 课程服务
5. `ml-sale`（SaleApp，:24104）—— 营销服务
6. `ml-order`（OrderApp，:24105）—— 订单服务

各服务启动类：

| 模块 | 启动类 |
|---|---|
| ml-gateway | `org.example.GatewayApp` |
| ml-user | `org.example.UserApp` |
| ml-course | `org.example.CourseApp` |
| ml-sale | `org.example.SaleApp` |
| ml-order | `org.example.OrderApp` |

> 各业务服务启动类统一标注 `@SpringBootApplication` + `@MapperScan("org.example.mapper")` + `@EnableDiscoveryClient`。

### 5.3 验证

- Nacos 控制台服务列表应出现 `ml-gateway`、`ml-user`、`ml-course`、`ml-sale`、`ml-order` 5 个服务。
- 访问各服务 Knife4j 接口文档（见 6.3）。

## 6. 前端启动

### 6.1 C 端用户站（ml-web）

```bash
cd /Users/caspian/my-lesson/ml-web
npm install
npm run dev
```

- 默认访问：Vite 输出地址（通常 `http://localhost:5173`）
- API 请求：`http://localhost:24101/`（网关），请求头自动携带 `token`
- 路由守卫：`requiresAuth` 页面未登录跳转登录页

### 6.2 管理后台（ml-web-admin）

```bash
cd /Users/caspian/my-lesson/ml-web-admin
npm install
npm run dev
```

- 页面覆盖：Dashboard、用户/角色/菜单管理、课程/季次/集次管理、优惠券/秒杀/通知/横幅/新闻、订单/订单明细、个人资料。

### 6.3 接口文档（Knife4j）

后端启动后访问：

| 服务 | 地址 |
|---|---|
| ml-user | `http://localhost:24102/doc.html` |
| ml-course | `http://localhost:24103/doc.html` |
| ml-sale | `http://localhost:24104/doc.html` |
| ml-order | `http://localhost:24105/doc.html` |

文档信息（标题/作者/描述）由 `springdoc.*` 配置驱动，公共配置类 `SpringDocConfig` 支持动态刷新。

## 7. 代码生成器（ml-generator）

工程使用 MyBatis-Flex Codegen 自动生成 entity/mapper/service/controller 等代码，属于开发工具，不参与运行。

### 7.1 使用方法

1. 确认数据库已初始化、`GenUtil` 中数据库连接信息正确：

```java
// ml-generator/src/main/java/org/example/util/GenUtil.java
private static final String MYSQL_HOST = "192.168.137.77:3306";
private static final String MYSQL_USERNAME = "root";
private static final String MYSQL_PASSWORD = "root";
private static final String AUTHOR = "尤洋辉";
private static final String BASE_PACKAGE = "org.example";
```

2. 在 `Main.java` 中按需放开对应模块的生成方法：

```java
// createUMS();   // 用户模块：ml_ums → ml-user
// createCMS();   // 课程模块：ml_cms → ml-course
// createSMS();   // 营销模块：ml_sms → ml-sale
createOMS();      // 订单模块：ml_oms → ml-order
```

3. 运行 `org.example.Main` 的 `main` 方法。

### 7.2 生成约定

| 项 | 约定 |
|---|---|
| 生成位置 | `{模块名}/src/main/java`，包名 `org.example` |
| 实体类 | Lombok + Swagger 注解，JDK17，覆盖已存在文件 |
| 逻辑删除 | 识别 `deleted` 字段（逻辑删除列） |
| 乐观锁 | 识别 `version` 字段（版本列） |
| TableDef | 生成实体辅助类 `*TableDef` |
| Controller 前缀 | `api/v1` |
| BigDecimal 映射 | 注册为 `Double` |

> ⚠️ 代码生成器会**覆盖已存在文件**（`overwriteEnable=true`），手工改动会被覆盖，建议只在新增表/新模块时使用，运行前先备份。

## 8. 换环境时需修改的位置清单

将项目迁移到新环境时，以下位置必须同步调整：

| 位置 | 内容 |
|---|---|
| 各服务 `bootstrap.yaml` | Nacos 地址 `192.168.137.77:8848`、MySQL 数据源地址 |
| `ml-sale` / `ml-order` `bootstrap.yaml` | RocketMQ NameServer `192.168.137.77:9876` |
| `ml-common/src/main/java/org/example/util/MinioUtil.java` | MinIO 端点/账号/密钥（硬编码） |
| `ml-generator/src/main/java/org/example/util/GenUtil.java` | MySQL 地址/账号/密码（硬编码） |
| Nacos 配置中心（`ml-group` 分组） | 网关端口/路由、Redis 连接、`token.white_list` |
| `ml-web/src/api/request.ts` | 网关 baseURL `http://localhost:24101/` |
| `ml-web-admin/src/request/index.js` | 管理端请求地址 |

## 9. 已知问题与注意事项

1. **ml-order 数据源指向 `ml_sms`**：`ml-order/src/main/resources/bootstrap.yaml` 中 `spring.datasource.url` 为 `jdbc:mysql://...:3306/ml_sms`，但订单表位于 `ml_oms` 库。当前代码未明显依赖该直连数据源（订单服务数据访问走 `ml_oms`），但如需使用该数据源请改为 `ml_oms`。
2. **ml-chat / ml-barrage 未开发**：仅存在 IDEA 默认 `Main.java`，无 `bootstrap.yaml`，启动它们无意义。
3. **前端调用了不存在的接口**：`ml-web/src/api/cms.ts` 中 `searchCourses` 调用 `/course-server/api/v1/course/search`，但后端 `CourseController` 未提供该接口，会 404，请以后端为准或补充该接口。
4. **网关端口依赖 Nacos**：仓库内 `ml-gateway/bootstrap.yaml` 未写 `server.port`，端口/路由在 Nacos 配置中心下发。
5. **秒杀锁为全局锁**：`SeckillServiceImpl.kill()` 中定义 `lockKey = "SK_LOCK:COURSE:" + fkCourseId` 但实际获取的是 `redissonClient.getLock("skLock")`（全局锁），所有课程共用一把锁，高并发下存在性能瓶颈，注释中的"分片"方案尚未落地。
