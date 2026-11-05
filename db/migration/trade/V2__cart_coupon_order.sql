-- lp_oms 交易表（V2）
-- 范围：购物车、优惠券模板、用户券、订单、订单明细五张表。
-- 字段与状态码按 engineering-plan 4.3.3 / 4.4 / 4.5；命名按 AGENTS.md 11.3。
-- 本迁移可重复执行（CREATE TABLE IF NOT EXISTS），重复执行不新增行、不清空数据。
-- 订单号与支付单号由应用生成；idempotency_key 由客户端传入，服务端以唯一索引兜底并发。

CREATE TABLE IF NOT EXISTS cart_item (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT '用户',
    course_id      BIGINT UNSIGNED NOT NULL COMMENT '课程',
    price_snapshot DECIMAL(10,2)   NOT NULL COMMENT '加车时价格（元）',
    version        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted        TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_course (user_id, course_id),
    KEY idx_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '购物车（课程类商品数量固定为 1，不设数量列）';

CREATE TABLE IF NOT EXISTS coupon (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    code             VARCHAR(32)    NOT NULL COMMENT '兑换口令',
    name             VARCHAR(64)    NOT NULL COMMENT '券名称',
    type             TINYINT        NOT NULL COMMENT '1 满减 / 2 折扣',
    threshold_amount DECIMAL(10,2)  NOT NULL DEFAULT 0 COMMENT '使用门槛（元）',
    discount_amount  DECIMAL(10,2)  NULL COMMENT '满减金额（元）',
    discount_rate    DECIMAL(4,2)   NULL COMMENT '折扣率（0.85）',
    total_count      INT             NOT NULL DEFAULT 0 COMMENT '发放总量，0 表示不限量',
    received_count   INT             NOT NULL DEFAULT 0 COMMENT '已领数量',
    start_at         DATETIME(3)     NOT NULL COMMENT '生效时间',
    end_at           DATETIME(3)     NOT NULL COMMENT '失效时间',
    status           TINYINT         NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    version          INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted          TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_status_time (status, start_at, end_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '优惠券模板';

CREATE TABLE IF NOT EXISTS user_coupon (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '领取人',
    coupon_id   BIGINT UNSIGNED NOT NULL COMMENT 'coupon.id',
    status      TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未使用 / 1 已使用 / 2 过期',
    order_id    BIGINT UNSIGNED NULL COMMENT '使用该券的订单',
    received_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '领取时间',
    used_at     DATETIME(3)     NULL COMMENT '使用时间',
    version     INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted     TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_coupon (user_id, coupon_id),
    KEY idx_user_status (user_id, status),
    KEY idx_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户券';

CREATE TABLE IF NOT EXISTS trade_order (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no        VARCHAR(32)     NOT NULL COMMENT '业务单号',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '下单用户',
    total_amount    DECIMAL(10,2)   NOT NULL COMMENT '原价合计（元）',
    discount_amount DECIMAL(10,2)   NOT NULL DEFAULT 0 COMMENT '优惠合计（元）',
    pay_amount      DECIMAL(10,2)   NOT NULL COMMENT '应付金额（元）',
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '0 待支付 / 1 已支付 / 2 已取消 / 3 已关闭 / 4 退款中 / 5 已退款',
    coupon_id       BIGINT UNSIGNED NULL COMMENT '使用的券',
    channel         TINYINT         NOT NULL COMMENT '1 支付宝（Web）/ 2 微信（小程序），下单时固定',
    pay_expire_at   DATETIME(3)     NOT NULL COMMENT '支付截止（下单后 30 分钟）',
    paid_at         DATETIME(3)     NULL COMMENT '支付时间',
    cancel_reason   VARCHAR(200)    NULL COMMENT '取消原因',
    idempotency_key VARCHAR(64)     NOT NULL COMMENT '客户端幂等键',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_idempotency (user_id, idempotency_key),
    KEY idx_user_status (user_id, status, created_at),
    KEY idx_status_expire (status, pay_expire_at),
    KEY idx_channel_status (channel, status, paid_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单';

CREATE TABLE IF NOT EXISTS trade_order_item (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id        BIGINT UNSIGNED NOT NULL COMMENT 'trade_order.id',
    course_id       BIGINT UNSIGNED NOT NULL COMMENT '课程',
    owner_teacher_id BIGINT UNSIGNED NOT NULL COMMENT '冗余归属教师，教师看板按教师聚合',
    course_title    VARCHAR(120)    NOT NULL COMMENT '下单时的课程标题快照',
    price           DECIMAL(10,2)   NOT NULL COMMENT '原价（元）',
    discount_amount DECIMAL(10,2)   NOT NULL DEFAULT 0 COMMENT '分摊优惠（元）',
    actual_amount   DECIMAL(10,2)   NOT NULL COMMENT '实付（元）',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_course (order_id, course_id),
    KEY idx_course (course_id),
    KEY idx_owner (owner_teacher_id, order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单明细';
