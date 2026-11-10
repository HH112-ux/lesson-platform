package com.lp.common.audit;

/**
 * Audit action codes stored in {@code admin_audit_log.action_type} and {@code
 * audit_outbox.action_type}.
 *
 * <p>The values are part of the cross-service contract: admin-service, course-service and
 * trade-service must agree on them. Only the three review actions covered by L1-7 are listed; the
 * unified audit is not a general purpose management log.
 *
 * @author Caspian
 * @since 0.6.0
 */
public final class AuditAction {

    /** A pending course was approved and published: {@code 0}. */
    public static final int COURSE_APPROVE = 0;

    /** A pending course was rejected and sent back to draft: {@code 1}. */
    public static final int COURSE_REJECT = 1;

    /** A pending refund was approved and the channel call started: {@code 2}. */
    public static final int REFUND_APPROVE = 2;

    /** A pending refund was rejected: {@code 3}. */
    public static final int REFUND_REJECT = 3;

    /** A pending teacher application was approved: {@code 4}. */
    public static final int TEACHER_APPROVE = 4;

    /** A pending teacher application was rejected: {@code 5}. */
    public static final int TEACHER_REJECT = 5;

    private AuditAction() {}
}
