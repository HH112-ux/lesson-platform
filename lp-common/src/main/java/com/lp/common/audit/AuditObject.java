package com.lp.common.audit;

/**
 * Object types stored in {@code admin_audit_log.object_type} and {@code audit_outbox.object_type}.
 *
 * <p>{@code object_id} carries the primary key of the matching domain row; the meaning of the
 * before and after status columns depends on this type.
 *
 * @author Caspian
 * @since 0.6.0
 */
public final class AuditObject {

    /** Course row in {@code lp_cms.course}: {@code 0}. */
    public static final int COURSE = 0;

    /** Refund request in {@code lp_oms.refund_request}: {@code 1}. */
    public static final int REFUND = 1;

    /** Teacher profile in {@code lp_cms.teacher_profile}: {@code 2}. */
    public static final int TEACHER = 2;

    /** Settle batch in {@code lp_oms.payment_settle_batch}: {@code 3}. */
    public static final int SETTLE_BATCH = 3;

    private AuditObject() {}
}
