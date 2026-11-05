-- lp_oms 权益发放本地消息表（V4）
-- 原因：支付回调与 0 元下单都在 trade 事务内完成，learn-service 属于另一个库（lp_lms）。
-- 事务内不做跨服务 HTTP：把“应发放权益”写入本地 outbox，同事务提交。
-- trade 侧定时中继读取 status=0 的记录，调用 learn-service 内部幂等接口；失败按退避重试。
-- 一致性为最终一致（不是恰好一次）：learn 侧 uk_user_course 吸收重复投递。

CREATE TABLE IF NOT EXISTS entitlement_outbox (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id     BIGINT UNSIGNED NOT NULL COMMENT 'trade_order.id',
    order_no     VARCHAR(64)     NOT NULL COMMENT '订单号，便于排查',
    user_id      BIGINT UNSIGNED NOT NULL COMMENT '学员',
    course_id    BIGINT UNSIGNED NOT NULL COMMENT '课程',
    status       TINYINT         NOT NULL DEFAULT 0 COMMENT '0 待投递 / 1 已投递 / 2 放弃',
    retry_count  INT             NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_at DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次投递时间',
    delivered_at DATETIME(3)     NULL COMMENT '投递成功时间',
    last_error   VARCHAR(300)    NULL COMMENT '最近一次失败原因（脱敏）',
    version      INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted      TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_course (order_id, course_id),
    KEY idx_status_retry (status, next_retry_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '权益发放本地消息表';

-- 回滚方式（仅限本地/开发环境，生产禁止破坏性回滚）：
-- 1) DROP TABLE entitlement_outbox;
-- 生产回滚以代码 revert 为准，表保留并由后续迁移兼容；已投递权益不回滚。
