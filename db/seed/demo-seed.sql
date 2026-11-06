-- Demo seed dataset for local end-to-end walkthroughs.
-- Scope: lp_cms (categories, teachers, courses, chapters, lessons), lp_ums (users, roles),
--        lp_oms (orders, order items, payments), lp_lms (entitlements, learning progress).
-- Idempotent by design: every business row is guarded by a natural-key NOT EXISTS check or
-- ON DUPLICATE KEY UPDATE, so re-running the file neither duplicates nor removes rows.
-- Table names are fully qualified so the file can be piped into a single mysql session.
-- The temporary tables below are session-scoped scaffolding only; they never store business data.

SET NAMES utf8mb4;

-- Temporary tables require a default schema; all business tables stay fully qualified.
USE lp_cms;

-- ---------------------------------------------------------------------------
-- 1. Seed inputs (session-scoped scaffolding)
-- ---------------------------------------------------------------------------

CREATE TEMPORARY TABLE IF NOT EXISTS demo_course_spec (
    course_no      INT           NOT NULL,
    category_slug  VARCHAR(60)   NOT NULL,
    teacher_name   VARCHAR(60)   NOT NULL,
    title          VARCHAR(120)  NOT NULL,
    subtitle       VARCHAR(200)  NOT NULL,
    description    VARCHAR(500)  NOT NULL,
    price          DECIMAL(10,2) NOT NULL,
    original_price DECIMAL(10,2) NOT NULL,
    level          TINYINT       NOT NULL,
    status         TINYINT       NOT NULL,
    sales_count    INT           NOT NULL,
    PRIMARY KEY (course_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT IGNORE INTO demo_course_spec
    (course_no, category_slug, teacher_name, title, subtitle, description,
     price, original_price, level, status, sales_count)
VALUES
    (1, 'career', '林知远', '高效沟通与向上管理', '把话说清楚，把事推动下去',
     '从表达结构、倾听反馈到向上汇报，拆解职场高频沟通场景，帮你在跨部门协作与向上管理中建立可复用的方法。',
     199.00, 299.00, 2, 2, 860),
    (2, 'career', '林知远', '结构化表达与工作汇报', '让汇报有结论、有重点、有下一步',
     '用结构化思维梳理信息，把零散工作整理成清晰结论，适用于周报、方案评审与当众表达。',
     129.00, 199.00, 1, 2, 612),
    (3, 'career', '林知远', '时间管理与深度工作', '把注意力留给真正重要的事',
     '从任务梳理、精力分配到专注块设计，建立一套可持续的时间管理习惯，减少被动忙碌。',
     0.00, 0.00, 1, 2, 745),
    (4, 'design', '苏晴', 'UI 设计系统实战', '从零搭建可落地的组件与规范',
     '以真实项目为主线，完成色彩、字体、间距到组件库的搭建，并输出可供团队协作的设计规范。',
     299.00, 399.00, 2, 2, 486),
    (5, 'design', '苏晴', '插画入门：从线稿到成稿', '零基础画出第一张完整插画',
     '从线条、形体到配色与光影，跟着案例完成一张完整插画，建立可继续练习的创作流程。',
     0.00, 0.00, 1, 2, 838),
    (6, 'design', '苏晴', '品牌视觉与配色方法', '让品牌在第一眼被记住',
     '拆解品牌视觉的构成要素与配色逻辑，通过案例讲解如何建立统一、可延展的视觉语言。',
     199.00, 259.00, 2, 3, 214),
    (7, 'development', '陈亦舟', 'Java 后端工程化实战', '从能跑到可维护的工程实践',
     '围绕分层结构、异常与日志、测试与构建，完整实践一套可维护的 Java 后端工程，并补齐上线前的质量门禁。',
     399.00, 499.00, 3, 2, 528),
    (8, 'development', '陈亦舟', 'MySQL 索引与性能优化', '看懂执行计划，拆掉慢查询',
     '从索引结构讲到执行计划分析，结合真实慢查询案例，掌握定位与优化数据库性能的系统方法。',
     249.00, 329.00, 2, 2, 367),
    (9, 'development', '陈亦舟', 'Spring Boot 微服务入门', '从单体到微服务的第一步',
     '以 Spring Boot 为基础，逐步搭建服务拆分、配置管理与服务调用，理解微服务架构的基本取舍。',
     0.00, 0.00, 1, 2, 691),
    (10, 'development', '陈亦舟', '前端工程化与构建优化', '让构建更快，让产物更小',
     '梳理前端工程化的关键环节，从依赖管理、构建配置到产物分析，系统优化开发体验与线上性能。',
     329.00, 429.00, 2, 3, 158),
    (11, 'career', '林知远', '跨部门协作实战手册', '把协作阻力拆成可推动的步骤',
     '围绕目标对齐、责任划分与进度同步，给出跨部门协作的可执行动作清单，减少来回拉扯。',
     159.00, 219.00, 2, 0, 0),
    (12, 'career', '林知远', '高质量会议的组织方法', '让每场会都有结论和下一步',
     '从会前议题设计、会中节奏控制到会后行动跟踪，建立一套可复用的会议组织方法。',
     129.00, 179.00, 1, 1, 0),
    (13, 'design', '苏晴', '设计作品集打磨', '让作品集讲清你的设计价值',
     '从项目筛选、叙事结构到视觉排版，逐项打磨一份能支撑面试与提案的作品集。',
     199.00, 269.00, 2, 0, 0),
    (14, 'design', '苏晴', '移动端界面设计规范', '在小屏幕上把信息排清楚',
     '围绕间距、字号、触控区域与适配规则，输出一份可直接落地的移动端设计规范。',
     249.00, 319.00, 2, 1, 0);

CREATE TEMPORARY TABLE IF NOT EXISTS demo_chapter_spec (
    course_no    INT          NOT NULL,
    chapter_sort INT          NOT NULL,
    title        VARCHAR(120) NOT NULL,
    PRIMARY KEY (course_no, chapter_sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT IGNORE INTO demo_chapter_spec (course_no, chapter_sort, title) VALUES
    (1, 1, '沟通的底层逻辑'),
    (1, 2, '把话讲清楚的表达结构'),
    (1, 3, '倾听、提问与反馈'),
    (1, 4, '跨部门协作的推动方法'),
    (1, 5, '向上管理与预期对齐'),
    (1, 6, '冲突处理与关系修复'),
    (2, 1, '结构化思维入门'),
    (2, 2, '结论先行的表达框架'),
    (2, 3, '工作汇报的信息组织'),
    (2, 4, '数据与事实的呈现'),
    (2, 5, '当众表达与答疑'),
    (3, 1, '重新认识时间与精力'),
    (3, 2, '任务梳理与优先级'),
    (3, 3, '深度工作的时间块'),
    (3, 4, '习惯养成与复盘'),
    (4, 1, '设计系统到底是什么'),
    (4, 2, '建立色彩与字体规范'),
    (4, 3, '间距、圆角与栅格'),
    (4, 4, '基础组件设计'),
    (4, 5, '业务组件与模式'),
    (4, 6, '规范交付与团队协作'),
    (5, 1, '工具与线条练习'),
    (5, 2, '形体与构图'),
    (5, 3, '配色与光影'),
    (5, 4, '完整作品实战'),
    (6, 1, '品牌视觉的构成'),
    (6, 2, '色彩心理与配色逻辑'),
    (6, 3, '主色、辅助色与中性色'),
    (6, 4, '视觉延展与场景应用'),
    (6, 5, '品牌规范与落地'),
    (7, 1, '工程结构与分层设计'),
    (7, 2, '统一响应与异常处理'),
    (7, 3, '日志、监控与排障'),
    (7, 4, '数据访问与事务'),
    (7, 5, '单元测试与集成测试'),
    (7, 6, '构建、打包与配置管理'),
    (7, 7, '接口契约与版本演进'),
    (7, 8, '上线检查与复盘'),
    (8, 1, '索引的数据结构与代价'),
    (8, 2, '联合索引与最左匹配'),
    (8, 3, '读懂执行计划'),
    (8, 4, '慢查询定位方法'),
    (8, 5, '分页、排序与覆盖索引'),
    (8, 6, '优化案例复盘'),
    (9, 1, 'Spring Boot 快速上手'),
    (9, 2, '配置管理与环境隔离'),
    (9, 3, '服务拆分与边界'),
    (9, 4, '服务调用与容错'),
    (9, 5, '部署与可观测性'),
    (10, 1, '工程化的边界与目标'),
    (10, 2, '依赖管理与版本策略'),
    (10, 3, '构建配置与插件'),
    (10, 4, '产物分析与体积优化'),
    (10, 5, '开发体验与热更新'),
    (10, 6, '持续集成与发布'),
    (11, 1, '协作前的目标对齐'),
    (11, 2, '推动跨团队落地'),
    (12, 1, '会前：议题与角色'),
    (12, 2, '会中与会后'),
    (13, 1, '作品筛选与叙事'),
    (13, 2, '排版与呈现'),
    (14, 1, '移动端设计基础'),
    (14, 2, '组件与适配');

CREATE TEMPORARY TABLE IF NOT EXISTS demo_lesson_spec (
    course_no    INT          NOT NULL,
    chapter_sort INT          NOT NULL,
    lesson_sort  INT          NOT NULL,
    title        VARCHAR(120) NOT NULL,
    PRIMARY KEY (course_no, chapter_sort, lesson_sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT IGNORE INTO demo_lesson_spec (course_no, chapter_sort, lesson_sort, title) VALUES
    (1, 1, 1, '沟通为什么总是对不上频道'),
    (1, 1, 2, '先处理情绪，再处理信息'),
    (1, 1, 3, '一次有效沟通的三个要素'),
    (1, 1, 4, '从我说完了到对方收到了'),
    (1, 2, 1, '一句话说清你的结论'),
    (1, 2, 2, '用三段式组织复杂信息'),
    (1, 2, 3, '把专业术语翻译成对方的话'),
    (1, 2, 4, '书面表达的减法练习'),
    (1, 3, 1, '倾听不是等对方说完'),
    (1, 3, 2, '用提问确认真实诉求'),
    (1, 3, 3, '给出可执行的反馈'),
    (1, 4, 1, '找到对方的利益点'),
    (1, 4, 2, '把请求变成共同目标'),
    (1, 4, 3, '协作卡住时怎么办'),
    (1, 5, 1, '理解上级的目标与压力'),
    (1, 5, 2, '主动同步进度与风险'),
    (1, 5, 3, '提出方案而不是只提问题'),
    (1, 6, 1, '冲突升级前的三个信号'),
    (1, 6, 2, '对事不对人的表达方式'),
    (1, 6, 3, '关系受损后的修复动作'),
    (2, 1, 1, '为什么你的表达总是很散'),
    (2, 1, 2, '归纳分组与逻辑顺序'),
    (2, 1, 3, '结论先行的思考习惯'),
    (2, 2, 1, '金字塔结构的四个要点'),
    (2, 2, 2, '用一句话概括核心结论'),
    (2, 2, 3, '论据的取舍与排序'),
    (2, 3, 1, '周报里应该写什么'),
    (2, 3, 2, '汇报中的背景与目标'),
    (2, 3, 3, '进展、风险与下一步'),
    (2, 3, 4, '让汇报支撑决策'),
    (2, 4, 1, '用数据说话的三个前提'),
    (2, 4, 2, '图表选择的基本原则'),
    (2, 4, 3, '避免误导性的表达'),
    (2, 5, 1, '开场三十秒抓住注意力'),
    (2, 5, 2, '应对追问的结构化回答'),
    (2, 5, 3, '紧张与节奏的控制'),
    (3, 1, 1, '时间管理的三个误区'),
    (3, 1, 2, '找到你的精力高峰'),
    (3, 1, 3, '记录一周时间开销'),
    (3, 2, 1, '把任务拆到可执行'),
    (3, 2, 2, '重要与紧急的再判断'),
    (3, 2, 3, '学会有依据地拒绝'),
    (3, 3, 1, '为专注预留完整时间'),
    (3, 3, 2, '减少打断的环境设计'),
    (3, 3, 3, '一次只做一件难事'),
    (3, 4, 1, '用最小行动启动习惯'),
    (3, 4, 2, '每周复盘的三问'),
    (3, 4, 3, '建立可持续的节奏'),
    (4, 1, 1, '设计系统与组件库的区别'),
    (4, 1, 2, '一套系统的组成要素'),
    (4, 1, 3, '从业务问题出发'),
    (4, 1, 4, '制定落地范围'),
    (4, 2, 1, '主色与语义色的划分'),
    (4, 2, 2, '中性色阶的搭建'),
    (4, 2, 3, '字体的层级与行高'),
    (4, 2, 4, '可访问性的对比度检查'),
    (4, 2, 5, '规范文档的写法'),
    (4, 3, 1, '用基础单位统一间距'),
    (4, 3, 2, '圆角与阴影的克制使用'),
    (4, 3, 3, '栅格与响应式断点'),
    (4, 3, 4, '布局组件的约束'),
    (4, 3, 5, '设计令牌的命名'),
    (4, 4, 1, '按钮的形态与状态'),
    (4, 4, 2, '输入框与表单控件'),
    (4, 4, 3, '选择器与下拉菜单'),
    (4, 4, 4, '提示与反馈组件'),
    (4, 5, 1, '从页面中抽取模式'),
    (4, 5, 2, '列表与卡片的组合'),
    (4, 5, 3, '空状态与异常状态'),
    (4, 5, 4, '组件变体的管理'),
    (4, 6, 1, '设计文件的组织结构'),
    (4, 6, 2, '与研发对齐实现方式'),
    (4, 6, 3, '版本迭代与变更记录'),
    (5, 1, 1, '画布、笔刷与图层'),
    (5, 1, 2, '线条的稳定与节奏'),
    (5, 1, 3, '从描线到默写'),
    (5, 1, 4, '常见线条问题修正'),
    (5, 2, 1, '用几何体概括形体'),
    (5, 2, 2, '画面重心与视线引导'),
    (5, 2, 3, '留白与疏密关系'),
    (5, 3, 1, '先定黑白灰关系'),
    (5, 3, 2, '配色的三个基本关系'),
    (5, 3, 3, '光源方向与明暗'),
    (5, 3, 4, '材质质感的表达'),
    (5, 4, 1, '从草图到线稿'),
    (5, 4, 2, '铺色与分层'),
    (5, 4, 3, '细节刻画与收尾'),
    (6, 1, 1, '品牌识别的四个层次'),
    (6, 1, 2, '标志、字体与图形'),
    (6, 1, 3, '视觉一致性的价值'),
    (6, 2, 1, '色彩传达的情绪'),
    (6, 2, 2, '同类色与对比色'),
    (6, 2, 3, '配色的比例关系'),
    (6, 2, 4, '从参考图中提取配色'),
    (6, 3, 1, '确定品牌主色'),
    (6, 3, 2, '辅助色的使用边界'),
    (6, 3, 3, '中性色支撑信息层级'),
    (6, 4, 1, '线上物料的延展'),
    (6, 4, 2, '线下场景的适配'),
    (6, 4, 3, '多品牌下的区分'),
    (6, 5, 1, '规范手册的结构'),
    (6, 5, 2, '与团队同步执行'),
    (6, 5, 3, '规范迭代的时机'),
    (7, 1, 1, '从脚手架到工程骨架'),
    (7, 1, 2, '分层边界与依赖方向'),
    (7, 1, 3, '领域模型与数据模型'),
    (7, 1, 4, '包结构与命名约定'),
    (7, 2, 1, '统一响应体的设计'),
    (7, 2, 2, '业务异常与系统异常'),
    (7, 2, 3, '全局异常处理与错误码'),
    (7, 2, 4, '参数校验的落地方式'),
    (7, 3, 1, '结构化日志的规范'),
    (7, 3, 2, '链路追踪与请求标识'),
    (7, 3, 3, '关键指标与告警'),
    (7, 3, 4, '线上问题排查路径'),
    (7, 4, 1, '数据访问层的职责'),
    (7, 4, 2, '事务边界与传播行为'),
    (7, 4, 3, '乐观锁与并发更新'),
    (7, 4, 4, '分页查询的常见坑'),
    (7, 5, 1, '测试分层的取舍'),
    (7, 5, 2, '用测试替身隔离依赖'),
    (7, 5, 3, '集成测试的数据准备'),
    (7, 5, 4, '让测试稳定可重复'),
    (7, 6, 1, 'Maven 多模块组织'),
    (7, 6, 2, '环境配置与密钥管理'),
    (7, 6, 3, '镜像构建与启动参数'),
    (7, 7, 1, '接口定义的稳定性'),
    (7, 7, 2, '兼容性变更的处理'),
    (7, 7, 3, '接口文档与联调'),
    (7, 8, 1, '上线前的检查清单'),
    (7, 8, 2, '灰度与回滚预案'),
    (7, 8, 3, '故障复盘的方法'),
    (8, 1, 1, 'B+ 树索引长什么样'),
    (8, 1, 2, '聚簇索引与二级索引'),
    (8, 1, 3, '索引的写入代价'),
    (8, 2, 1, '联合索引的排列顺序'),
    (8, 2, 2, '最左匹配的实际影响'),
    (8, 2, 3, '索引下推与回表'),
    (8, 2, 4, '什么时候该建联合索引'),
    (8, 3, 1, 'EXPLAIN 的关键字段'),
    (8, 3, 2, '访问类型与扫描行数'),
    (8, 3, 3, '常见的类型转换陷阱'),
    (8, 3, 4, '用 EXPLAIN ANALYZE 验证'),
    (8, 4, 1, '开启慢查询日志'),
    (8, 4, 2, '从耗时分布找问题'),
    (8, 4, 3, '复现与压测'),
    (8, 5, 1, '深分页为什么慢'),
    (8, 5, 2, '排序与临时表'),
    (8, 5, 3, '覆盖索引减少回表'),
    (8, 5, 4, '分页方案的取舍'),
    (8, 6, 1, '订单列表查询优化'),
    (8, 6, 2, '统计查询的改写'),
    (8, 6, 3, '索引上线后的验证'),
    (9, 1, 1, '创建第一个服务'),
    (9, 1, 2, '自动配置与起步依赖'),
    (9, 1, 3, '配置文件与 Profiles'),
    (9, 2, 1, '配置分层与环境区分'),
    (9, 2, 2, '配置中心的基本用法'),
    (9, 2, 3, '敏感配置的处理'),
    (9, 3, 1, '什么时候需要拆分'),
    (9, 3, 2, '按业务能力划分服务'),
    (9, 3, 3, '数据归属与跨服务查询'),
    (9, 3, 4, '拆分后的常见问题'),
    (9, 4, 1, '同步调用与超时'),
    (9, 4, 2, '重试与熔断'),
    (9, 4, 3, '幂等设计'),
    (9, 5, 1, '容器化与启动配置'),
    (9, 5, 2, '健康检查与就绪探针'),
    (9, 5, 3, '日志、指标与追踪'),
    (10, 1, 1, '工程化解决什么问题'),
    (10, 1, 2, '从项目模板开始'),
    (10, 1, 3, '团队规范的落地'),
    (10, 2, 1, '依赖安装与锁文件'),
    (10, 2, 2, '版本范围与升级策略'),
    (10, 2, 3, '依赖体积与安全审计'),
    (10, 3, 1, '构建流程的几个阶段'),
    (10, 3, 2, '插件与加载器'),
    (10, 3, 3, '环境变量与多环境构建'),
    (10, 3, 4, '产物路径与缓存策略'),
    (10, 4, 1, '分析产物体积'),
    (10, 4, 2, '代码分割与懒加载'),
    (10, 4, 3, '公共依赖的抽离'),
    (10, 4, 4, '图片与静态资源优化'),
    (10, 5, 1, '本地启动速度优化'),
    (10, 5, 2, '热更新与状态保持'),
    (10, 5, 3, '调试工具与断点'),
    (10, 6, 1, '流水线的阶段划分'),
    (10, 6, 2, '构建缓存与并行'),
    (10, 6, 3, '发布与回滚'),
    (11, 1, 1, '识别协作中的真实阻力'),
    (11, 1, 2, '把目标写成双方认可的一句话'),
    (11, 2, 1, '责任矩阵与接口人'),
    (11, 2, 2, '进度同步与风险升级'),
    (12, 1, 1, '判断这场会该不该开'),
    (12, 1, 2, '议题、结论与角色预设'),
    (12, 2, 1, '会中控场与分歧收敛'),
    (12, 2, 2, '行动项跟踪与复盘'),
    (13, 1, 1, '选项目的三个标准'),
    (13, 1, 2, '用故事线串起项目价值'),
    (13, 2, 1, '版式、留白与信息层级'),
    (13, 2, 2, '一页作品的自检清单'),
    (14, 1, 1, '移动端的信息密度'),
    (14, 1, 2, '触控区域与可读性'),
    (14, 2, 1, '组件拆分与复用'),
    (14, 2, 2, '多尺寸适配与验收');

-- ---------------------------------------------------------------------------
-- 2. lp_cms: categories, teachers, courses, chapters, lessons
-- ---------------------------------------------------------------------------

INSERT INTO lp_cms.course_category (parent_id, name, slug, sort, status)
VALUES (0, '职场技能', 'career', 10, 1),
       (0, '设计创意', 'design', 20, 1),
       (0, '编程开发', 'development', 30, 1)
ON DUPLICATE KEY UPDATE name   = VALUES(name),
                        sort   = VALUES(sort),
                        status = VALUES(status);

INSERT INTO lp_cms.teacher_profile (user_id, display_name, avatar_url, bio, status)
SELECT NULL,
       '林知远',
       'https://picsum.photos/seed/lp-teacher-lin/200/200',
       '职场沟通与表达教练，服务过多家企业的管理者培训项目。',
       1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM lp_cms.teacher_profile WHERE display_name = '林知远');

INSERT INTO lp_cms.teacher_profile (user_id, display_name, avatar_url, bio, status)
SELECT NULL,
       '苏晴',
       'https://picsum.photos/seed/lp-teacher-su/200/200',
       '产品设计师，长期负责设计系统与品牌视觉落地。',
       1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM lp_cms.teacher_profile WHERE display_name = '苏晴');

INSERT INTO lp_cms.teacher_profile (user_id, display_name, avatar_url, bio, status)
SELECT NULL,
       '陈亦舟',
       'https://picsum.photos/seed/lp-teacher-chen/200/200',
       '后端工程师，专注 Java 工程化、数据库性能与微服务治理。',
       1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM lp_cms.teacher_profile WHERE display_name = '陈亦舟');

INSERT INTO lp_cms.course
    (category_id, owner_teacher_id, title, subtitle, cover_url, description,
     price, original_price, instructor_id, level, status, sales_count, published_at)
SELECT cat.id,
       t.id,
       s.title,
       s.subtitle,
       CONCAT('https://picsum.photos/seed/lp-course-', s.course_no, '/800/450'),
       s.description,
       s.price,
       s.original_price,
       t.id,
       s.level,
       s.status,
       s.sales_count,
       CASE WHEN s.status = 2 THEN NOW(3) ELSE NULL END
FROM demo_course_spec s
         JOIN lp_cms.course_category cat ON cat.slug = s.category_slug
         JOIN lp_cms.teacher_profile t ON t.display_name = s.teacher_name
WHERE NOT EXISTS (SELECT 1 FROM lp_cms.course c WHERE c.title = s.title);

INSERT INTO lp_cms.course_chapter (course_id, title, sort, status)
SELECT c.id, s.title, s.chapter_sort, 1
FROM demo_chapter_spec s
         JOIN demo_course_spec cs ON cs.course_no = s.course_no
         JOIN lp_cms.course c ON c.title = cs.title
WHERE NOT EXISTS (SELECT 1
                  FROM lp_cms.course_chapter ch
                  WHERE ch.course_id = c.id
                    AND ch.sort = s.chapter_sort);

-- Every lesson of a free course is playable; paid courses expose the first lesson of
-- chapter 1 as a trial, plus one extra trial lesson in two courses for variety.
INSERT INTO lp_cms.course_lesson
    (course_id, chapter_id, title, video_url, video_duration_sec, video_codec,
     is_free, sort, status)
SELECT c.id,
       ch.id,
       ls.title,
       CONCAT('demo/course-', ls.course_no, '/ch', ls.chapter_sort,
              '/lesson-', ls.lesson_sort, '.mp4'),
       300 + ((ls.course_no * 37 + ls.chapter_sort * 53 + ls.lesson_sort * 71) % 41) * 25,
       'h264',
       CASE
           WHEN cs.price = 0 THEN 1
           WHEN ls.chapter_sort = 1 AND ls.lesson_sort = 1 THEN 1
           WHEN ls.course_no = 4 AND ls.chapter_sort = 2 AND ls.lesson_sort = 2 THEN 1
           WHEN ls.course_no = 8 AND ls.chapter_sort = 6 AND ls.lesson_sort = 3 THEN 1
           ELSE 0
           END,
       ls.lesson_sort,
       1
FROM demo_lesson_spec ls
         JOIN demo_course_spec cs ON cs.course_no = ls.course_no
         JOIN lp_cms.course c ON c.title = cs.title
         JOIN lp_cms.course_chapter ch
              ON ch.course_id = c.id AND ch.sort = ls.chapter_sort
WHERE NOT EXISTS (SELECT 1
                  FROM lp_cms.course_lesson l
                  WHERE l.chapter_id = ch.id
                    AND l.sort = ls.lesson_sort);

-- ---------------------------------------------------------------------------
-- 3. lp_ums: users and role assignments
-- BCrypt hash below is the demo password Demo@123456; it is a local demo value only.
-- On re-run the hash is intentionally left untouched.
-- ---------------------------------------------------------------------------

INSERT INTO lp_ums.sys_user (username, password_hash, nickname, status, gender)
VALUES ('demo_admin', '$2y$10$AUmRkJxX1lqeko0ddyNMIu5s1lliTyc/4HRi5UUEOpvYILQllIegi', '平台管理员', 1, 0)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname),
                        status   = VALUES(status);

INSERT INTO lp_ums.sys_user (username, password_hash, nickname, status, gender)
VALUES ('demo_buyer', '$2y$10$AUmRkJxX1lqeko0ddyNMIu5s1lliTyc/4HRi5UUEOpvYILQllIegi', '学员小柯', 1, 0)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname),
                        status   = VALUES(status);

INSERT INTO lp_ums.sys_user (username, password_hash, nickname, status, gender)
VALUES ('demo_guest', '$2y$10$AUmRkJxX1lqeko0ddyNMIu5s1lliTyc/4HRi5UUEOpvYILQllIegi', '访客小林', 1, 0)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname),
                        status   = VALUES(status);

INSERT INTO lp_ums.sys_user (username, password_hash, nickname, status, gender)
VALUES ('demo_teacher_lin', '$2y$10$AUmRkJxX1lqeko0ddyNMIu5s1lliTyc/4HRi5UUEOpvYILQllIegi', '林知远', 1, 1)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname),
                        status   = VALUES(status);

INSERT INTO lp_ums.sys_user (username, password_hash, nickname, status, gender)
VALUES ('demo_teacher_su', '$2y$10$AUmRkJxX1lqeko0ddyNMIu5s1lliTyc/4HRi5UUEOpvYILQllIegi', '苏晴', 1, 2)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname),
                        status   = VALUES(status);

INSERT INTO lp_ums.sys_user (username, password_hash, nickname, status, gender)
VALUES ('demo_ops', '$2y$10$AUmRkJxX1lqeko0ddyNMIu5s1lliTyc/4HRi5UUEOpvYILQllIegi', '运营小周', 1, 0)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname),
                        status   = VALUES(status);

INSERT INTO lp_ums.sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM lp_ums.sys_user u
         JOIN lp_ums.sys_role r ON r.code = 'ADMIN'
WHERE u.username = 'demo_admin'
  AND NOT EXISTS (SELECT 1
                  FROM lp_ums.sys_user_role ur
                  WHERE ur.user_id = u.id
                    AND ur.role_id = r.id);

INSERT INTO lp_ums.sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM lp_ums.sys_user u
         JOIN lp_ums.sys_role r ON r.code = 'STUDENT'
WHERE u.username = 'demo_buyer'
  AND NOT EXISTS (SELECT 1
                  FROM lp_ums.sys_user_role ur
                  WHERE ur.user_id = u.id
                    AND ur.role_id = r.id);

INSERT INTO lp_ums.sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM lp_ums.sys_user u
         JOIN lp_ums.sys_role r ON r.code = 'STUDENT'
WHERE u.username = 'demo_guest'
  AND NOT EXISTS (SELECT 1
                  FROM lp_ums.sys_user_role ur
                  WHERE ur.user_id = u.id
                    AND ur.role_id = r.id);

INSERT INTO lp_ums.sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM lp_ums.sys_user u
         JOIN lp_ums.sys_role r ON r.code = 'TEACHER'
WHERE u.username IN ('demo_teacher_lin', 'demo_teacher_su')
  AND NOT EXISTS (SELECT 1
                  FROM lp_ums.sys_user_role ur
                  WHERE ur.user_id = u.id
                    AND ur.role_id = r.id);

INSERT INTO lp_ums.sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM lp_ums.sys_user u
         JOIN lp_ums.sys_role r ON r.code = 'OPS'
WHERE u.username = 'demo_ops'
  AND NOT EXISTS (SELECT 1
                  FROM lp_ums.sys_user_role ur
                  WHERE ur.user_id = u.id
                    AND ur.role_id = r.id);

-- Bind the demo teacher profiles to their login accounts so /api/v1/teacher/courses can
-- filter by owner. Existing bindings are left untouched.
UPDATE lp_cms.teacher_profile t
    JOIN lp_ums.sys_user u ON u.username = 'demo_teacher_lin'
SET t.user_id = u.id
WHERE t.display_name = '林知远'
  AND t.user_id IS NULL;

UPDATE lp_cms.teacher_profile t
    JOIN lp_ums.sys_user u ON u.username = 'demo_teacher_su'
SET t.user_id = u.id
WHERE t.display_name = '苏晴'
  AND t.user_id IS NULL;

-- ---------------------------------------------------------------------------
-- 4. lp_oms: paid orders of demo_buyer (Alipay channel only)
-- ---------------------------------------------------------------------------

SET @demo_buyer_id = (SELECT id FROM lp_ums.sys_user WHERE username = 'demo_buyer' LIMIT 1);
SET @demo_course_1_id = (SELECT id FROM lp_cms.course WHERE title = '高效沟通与向上管理' LIMIT 1);
SET @demo_course_7_id = (SELECT id FROM lp_cms.course WHERE title = 'Java 后端工程化实战' LIMIT 1);
SET @demo_teacher_career_id = (SELECT id FROM lp_cms.teacher_profile WHERE display_name = '林知远' LIMIT 1);
SET @demo_teacher_dev_id = (SELECT id FROM lp_cms.teacher_profile WHERE display_name = '陈亦舟' LIMIT 1);

INSERT INTO lp_oms.trade_order
    (order_no, user_id, total_amount, discount_amount, pay_amount, status,
     channel, pay_expire_at, paid_at, idempotency_key)
SELECT 'LPDEMO20260101000001', @demo_buyer_id, 199.00, 0.00, 199.00, 1,
       1, NULL, NOW(3), 'demo-seed-order-1'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM lp_oms.trade_order WHERE order_no = 'LPDEMO20260101000001');

SET @demo_order_1_id = (SELECT id FROM lp_oms.trade_order WHERE order_no = 'LPDEMO20260101000001' LIMIT 1);

INSERT INTO lp_oms.trade_order_item
    (order_id, course_id, owner_teacher_id, course_title, price, discount_amount, actual_amount)
SELECT @demo_order_1_id, @demo_course_1_id, @demo_teacher_career_id,
       '高效沟通与向上管理', 199.00, 0.00, 199.00
FROM DUAL
WHERE NOT EXISTS (SELECT 1
                  FROM lp_oms.trade_order_item
                  WHERE order_id = @demo_order_1_id
                    AND course_id = @demo_course_1_id);

INSERT INTO lp_oms.payment_record
    (order_id, pay_no, channel, pay_scene, amount, status, paid_at)
SELECT @demo_order_1_id, 'LPDEMOPAY0001', 1, 1, 199.00, 1, NOW(3)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM lp_oms.payment_record WHERE pay_no = 'LPDEMOPAY0001');

INSERT INTO lp_oms.trade_order
    (order_no, user_id, total_amount, discount_amount, pay_amount, status,
     channel, pay_expire_at, paid_at, idempotency_key)
SELECT 'LPDEMO20260101000002', @demo_buyer_id, 399.00, 0.00, 399.00, 1,
       1, NULL, NOW(3), 'demo-seed-order-2'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM lp_oms.trade_order WHERE order_no = 'LPDEMO20260101000002');

SET @demo_order_2_id = (SELECT id FROM lp_oms.trade_order WHERE order_no = 'LPDEMO20260101000002' LIMIT 1);

INSERT INTO lp_oms.trade_order_item
    (order_id, course_id, owner_teacher_id, course_title, price, discount_amount, actual_amount)
SELECT @demo_order_2_id, @demo_course_7_id, @demo_teacher_dev_id,
       'Java 后端工程化实战', 399.00, 0.00, 399.00
FROM DUAL
WHERE NOT EXISTS (SELECT 1
                  FROM lp_oms.trade_order_item
                  WHERE order_id = @demo_order_2_id
                    AND course_id = @demo_course_7_id);

INSERT INTO lp_oms.payment_record
    (order_id, pay_no, channel, pay_scene, amount, status, paid_at)
SELECT @demo_order_2_id, 'LPDEMOPAY0002', 1, 1, 399.00, 1, NOW(3)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM lp_oms.payment_record WHERE pay_no = 'LPDEMOPAY0002');

-- ---------------------------------------------------------------------------
-- 5. lp_lms: entitlements and learning progress of demo_buyer
-- ---------------------------------------------------------------------------

INSERT INTO lp_lms.user_entitlement (user_id, course_id, order_id, source)
SELECT @demo_buyer_id, @demo_course_1_id, @demo_order_1_id, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1
                  FROM lp_lms.user_entitlement
                  WHERE user_id = @demo_buyer_id
                    AND course_id = @demo_course_1_id);

INSERT INTO lp_lms.user_entitlement (user_id, course_id, order_id, source)
SELECT @demo_buyer_id, @demo_course_7_id, @demo_order_2_id, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1
                  FROM lp_lms.user_entitlement
                  WHERE user_id = @demo_buyer_id
                    AND course_id = @demo_course_7_id);

-- Course 1, chapter 1, lesson 1: fully watched and marked completed.
INSERT INTO lp_lms.learning_progress
    (user_id, course_id, lesson_id, position_seconds, max_position_seconds, watched_sec, completed)
SELECT @demo_buyer_id,
       l.course_id,
       l.id,
       l.video_duration_sec,
       l.video_duration_sec,
       l.video_duration_sec,
       1
FROM lp_cms.course_lesson l
         JOIN lp_cms.course_chapter ch ON ch.id = l.chapter_id
WHERE l.course_id = @demo_course_1_id
  AND ch.sort = 1
  AND l.sort = 1
  AND NOT EXISTS (SELECT 1
                  FROM lp_lms.learning_progress p
                  WHERE p.user_id = @demo_buyer_id
                    AND p.lesson_id = l.id);

-- Course 1, chapter 1, lesson 2: watched roughly 40 percent.
INSERT INTO lp_lms.learning_progress
    (user_id, course_id, lesson_id, position_seconds, max_position_seconds, watched_sec, completed)
SELECT @demo_buyer_id,
       l.course_id,
       l.id,
       ROUND(l.video_duration_sec * 0.4),
       ROUND(l.video_duration_sec * 0.4),
       ROUND(l.video_duration_sec * 0.4),
       0
FROM lp_cms.course_lesson l
         JOIN lp_cms.course_chapter ch ON ch.id = l.chapter_id
WHERE l.course_id = @demo_course_1_id
  AND ch.sort = 1
  AND l.sort = 2
  AND NOT EXISTS (SELECT 1
                  FROM lp_lms.learning_progress p
                  WHERE p.user_id = @demo_buyer_id
                    AND p.lesson_id = l.id);

-- Course 7, chapter 1, lesson 1: watched roughly 40 percent.
INSERT INTO lp_lms.learning_progress
    (user_id, course_id, lesson_id, position_seconds, max_position_seconds, watched_sec, completed)
SELECT @demo_buyer_id,
       l.course_id,
       l.id,
       ROUND(l.video_duration_sec * 0.4),
       ROUND(l.video_duration_sec * 0.4),
       ROUND(l.video_duration_sec * 0.4),
       0
FROM lp_cms.course_lesson l
         JOIN lp_cms.course_chapter ch ON ch.id = l.chapter_id
WHERE l.course_id = @demo_course_7_id
  AND ch.sort = 1
  AND l.sort = 1
  AND NOT EXISTS (SELECT 1
                  FROM lp_lms.learning_progress p
                  WHERE p.user_id = @demo_buyer_id
                    AND p.lesson_id = l.id);
