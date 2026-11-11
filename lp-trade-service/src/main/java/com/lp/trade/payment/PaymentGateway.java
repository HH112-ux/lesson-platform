package com.lp.trade.payment;

import com.lp.trade.dto.PrepayResult;
import com.lp.trade.entity.PaymentRecord;
import java.math.BigDecimal;

/**
 * Builds a channel specific prepay request.
 *
 * <p>This is a port so the payment service has no provider dependency. The placeholder
 * implementation always fails with {@code 50205}; a real implementation signs the request with
 * channel credentials and returns the parameters the client SDK needs.
 *
 * @author Caspian
 * @since 0.1.0
 */
public interface PaymentGateway {

    /**
     * Creates a prepay request at the channel.
     *
     * @param payment payment attempt already persisted locally
     * @return channel parameters for the client SDK
     * @throws com.lp.common.exception.BizException when credentials are missing or the channel
     *     fails
     * @author Caspian
     * @since 0.1.0
     */
    PrepayResult prepay(PaymentRecord payment);

    /**
     * Sends a refund to the channel.
     *
     * <p>The refund number is the channel idempotency key: replaying a refund after a crash returns
     * the same refund at the provider instead of moving money twice.
     *
     * <p><strong>Exception contract:</strong> {@code BizException} means the channel definitely did
     * not move money. Implementations may raise it only when the request was rejected before
     * execution or when the channel answered with a subcode that proves no refund took place. A
     * timeout, a reset connection, an unreadable response or an ambiguous rejection must raise
     * {@link ChannelOutcomeUnknownException} instead: the refund service treats {@code
     * BizException} as safe to retry, so misclassifying an uncertain outcome would refund twice.
     *
     * @param payment successful payment to refund
     * @param refundNo local refund number, used as the channel idempotency key
     * @param amount amount in yuan, never above {@code payment.amount}
     * @return channel refund reference; channels without a separate refund number return the out
     *     request number they sent
     * @throws com.lp.common.exception.BizException only when the channel definitely did not move
     *     money
     * @throws ChannelOutcomeUnknownException when the outcome cannot be determined and the refund
     *     must be parked, never retried automatically
     * @author Caspian
     * @since 0.1.0
     */
    String refund(PaymentRecord payment, String refundNo, BigDecimal amount);

    /**
     * Reads the current state of a trade at the channel.
     *
     * <p>Used to converge local state when a close did not produce a definite answer, and to detect
     * a payment whose callback never arrived.
     *
     * @param payment payment attempt to inspect
     * @return channel state, never {@code null}; a trade the channel does not know is {@link
     *     ChannelTradeState#notFound()}
     * @throws com.lp.common.exception.BizException when the channel request fails
     * @author Caspian
     * @since 0.5.0
     */
    ChannelTradeState query(PaymentRecord payment);

    /**
     * Closes a trade at the channel so the buyer can no longer pay it.
     *
     * <p>A trade the channel never created returns {@code true}. A channel may reject a close it
     * cannot apply, such as a trade that already moved to a paid state; the caller then queries the
     * trade and converges instead of retrying the close blindly.
     *
     * @param payment pending payment attempt
     * @return {@code true} when no further payment can complete on this trade
     * @throws com.lp.common.exception.BizException when the channel rejects the request, the
     *     request times out, or credentials are missing
     * @author Caspian
     * @since 0.5.0
     */
    boolean close(PaymentRecord payment);

    /**
     * Reads the refund state for one out request number at the channel.
     *
     * <p>The call is read-only and never moves money. A refund the channel does not report is
     * returned as {@link ChannelRefundState#notFound()}; that answer is not proof that no refund
     * happened, because channel ledgers can lag. Callers must never use it to retry a refund or to
     * mark one failed.
     *
     * @param payment successful payment being refunded
     * @param refundNo out request number previously sent as the channel idempotency key
     * @return channel refund state, never {@code null}
     * @throws com.lp.common.exception.BizException when the channel request fails or is rejected
     * @throws ChannelOutcomeUnknownException when the response cannot be read; callers must leave
     *     local state unchanged
     * @author Caspian
     * @since 0.6.0
     */
    ChannelRefundState refundQuery(PaymentRecord payment, String refundNo);
}
