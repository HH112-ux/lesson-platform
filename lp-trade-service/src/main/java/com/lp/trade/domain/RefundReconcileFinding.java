package com.lp.trade.domain;

/**
 * Reconciliation finding codes stored in {@code refund_request.reconcile_finding}.
 *
 * <p>The database keeps a {@code TINYINT} code, never the label, so the column stays small and the
 * vocabulary is enforced by this class. Logs use {@link #nameOf(Integer)} to print readable text.
 * {@link #CHANNEL_DONE} means "the channel confirmed the refund and the local row has not
 * advanced", whoever produced the finding: the reconciliation read-back and the review path both
 * store it, and automatic convergence settles it. It must never be turned into {@code FAILED}
 * automatically.
 *
 * @author Caspian
 * @since 0.7.0
 */
public final class RefundReconcileFinding {

    /** No read-back has produced a finding yet. */
    public static final int NONE = 0;

    /**
     * The channel confirmed the refund for exactly the requested amount while the local row is
     * still approved or unknown, regardless of who wrote the finding.
     */
    public static final int CHANNEL_DONE = 1;

    /** The channel has no refund record under the local request number. */
    public static final int CHANNEL_NOT_FOUND = 2;

    /** The channel answered with another status or another amount. */
    public static final int CHANNEL_OTHER = 3;

    /** The read-back itself failed. */
    public static final int QUERY_FAILED = 4;

    /** The local payment is missing or not successful, so the channel was not asked. */
    public static final int PAYMENT_UNSETTLED = 5;

    private RefundReconcileFinding() {}

    /**
     * Returns the readable label of one finding code for logs.
     *
     * @param finding finding code, {@code null} before the first scan
     * @return label of the code, or the raw number for an unknown code, or {@code null} when absent
     * @author Caspian
     * @since 0.7.0
     */
    public static String nameOf(Integer finding) {
        if (finding == null) {
            return null;
        }
        switch (finding) {
            case NONE:
                return "NONE";
            case CHANNEL_DONE:
                return "CHANNEL_DONE";
            case CHANNEL_NOT_FOUND:
                return "CHANNEL_NOT_FOUND";
            case CHANNEL_OTHER:
                return "CHANNEL_OTHER";
            case QUERY_FAILED:
                return "QUERY_FAILED";
            case PAYMENT_UNSETTLED:
                return "PAYMENT_UNSETTLED";
            default:
                return String.valueOf(finding);
        }
    }
}
