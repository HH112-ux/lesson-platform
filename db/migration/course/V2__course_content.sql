-- lp_cms 课程内容表（V2）
-- 范围：课程、章节、课时三张表，供 course-service 内部查询接口与 trade-service 下单校验使用。
-- 分类、多讲师与日统计表在需要时追加，本迁移不凭空新增业务表。
-- 命名遵循 AGENTS.md 11.3：表名 snake_case 单数、外键 {表}_id、时间字段 *_at、唯一键 uk_、索引 idx_。
-- 本迁移可重复执行（CREATE TABLE IF NOT EXISTS），重复执行不新增行。

CREATE TABLE IF NOT EXISTS course (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_id     BIGINT UNSIGNED NOT NULL COMMENT '二级分类',
    owner_teacher_id BIGINT UNSIGNED NOT NULL COMMENT '归属教师（teacher_profile.id）',
    title           VARCHAR(120)    NOT NULL COMMENT '课程标题',
    subtitle        VARCHAR(200)    NULL COMMENT '副标题/卖点',
    cover_url       VARCHAR(255)    NOT NULL COMMENT '封面地址',
    description     TEXT            NULL COMMENT '详情正文',
    price           DECIMAL(10,2)   NOT NULL DEFAULT 0 COMMENT '售价（元），运营定价',
    original_price  DECIMAL(10,2)   NULL COMMENT '划线价',
    instructor_id   BIGINT UNSIGNED NOT NULL COMMENT '主讲 teacher_profile.id',
    level           TINYINT         NOT NULL DEFAULT 1 COMMENT '1 入门 / 2 进阶 / 3 高级',
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '0 草稿 / 1 待审核 / 2 已上架 / 3 已下架',
    review_remark   VARCHAR(300)    NULL COMMENT '审核意见',
    submitted_at    DATETIME(3)     NULL COMMENT '提交审核时间',
    reviewed_by     BIGINT UNSIGNED NULL COMMENT '审核人',
    reviewed_at     DATETIME(3)     NULL COMMENT '审核时间',
    sales_count     INT             NOT NULL DEFAULT 0 COMMENT '冗余销量',
    published_at    DATETIME(3)     NULL COMMENT '上架时间',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_category_status (category_id, status),
    KEY idx_owner_status (owner_teacher_id, status, updated_at),
    KEY idx_instructor (instructor_id),
    KEY idx_status_published (status, published_at),
    FULLTEXT KEY ft_title_subtitle (title, subtitle) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课程';

CREATE TABLE IF NOT EXISTS course_chapter (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    course_id  BIGINT UNSIGNED NOT NULL COMMENT 'course.id',
    title      VARCHAR(120)    NOT NULL COMMENT '章节标题',
    sort       INT             NOT NULL DEFAULT 0 COMMENT '排序',
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '1 正常 / 0 隐藏',
    version    INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_course_sort (course_id, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课程章节';

CREATE TABLE IF NOT EXISTS course_lesson (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    course_id         BIGINT UNSIGNED NOT NULL COMMENT 'course.id，冗余便于按课程查',
    chapter_id        BIGINT UNSIGNED NOT NULL COMMENT 'course_chapter.id',
    title             VARCHAR(120)    NOT NULL COMMENT '课时标题',
    video_url         VARCHAR(500)    NOT NULL COMMENT 'MinIO 对象键（非公网直链）',
    video_duration_sec INT            NOT NULL DEFAULT 0 COMMENT '时长（秒）',
    video_codec       VARCHAR(16)     NOT NULL DEFAULT 'h264' COMMENT '视频编码，仅接受 h264',
    video_width       INT             NOT NULL DEFAULT 0 COMMENT '视频宽',
    video_height      INT             NOT NULL DEFAULT 0 COMMENT '视频高',
    video_size_bytes  BIGINT          NOT NULL DEFAULT 0 COMMENT '文件大小',
    video_sha256      CHAR(64)        NOT NULL DEFAULT '' COMMENT '内容哈希',
    cover_url         VARCHAR(255)    NULL COMMENT '封面地址',
    is_free           TINYINT         NOT NULL DEFAULT 0 COMMENT '1 试看 / 0 需购买',
    sort              INT             NOT NULL DEFAULT 0 COMMENT '排序',
    status            TINYINT         NOT NULL DEFAULT 1 COMMENT '1 正常 / 0 隐藏',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted           TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_chapter_sort (chapter_id, sort),
    KEY idx_course_status (course_id, status),
    KEY idx_sha256 (video_sha256)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课时';
