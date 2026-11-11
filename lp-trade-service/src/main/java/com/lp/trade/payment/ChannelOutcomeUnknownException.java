package com.lp.trade.payment;

/**
 * Raised when a channel call may have moved money but the outcome cannot be read.
 *
 * <p>The refund service parks the refund as unknown instead of retrying: a retry could move money
 * twice, and a missing or unreadable answer is never proof that nothing happened. Implementations
 * must raise this instead of {@code BizException} for timeouts, reset connections, unreadable
 * responses and ambiguous business rejections.
 *
 * @author Caspian
 * @since 0.6.0
 */
public class ChannelOutcomeUnknownException extends RuntimeException {

    /**
     * Creates the exception with a message.
     *
     * @param message description of the unreadable outcome
     * @author Caspian
     * @since 0.6.0
     */
    public ChannelOutcomeUnknownException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and the underlying transport failure.
     *
     * @param message description of the unreadable outcome
     * @param cause original failure
     * @author Caspian
     * @since 0.6.0
     */
    public ChannelOutcomeUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
