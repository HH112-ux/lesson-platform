package com.lp.trade.service;

import com.lp.trade.dto.PaymentNotification;
import com.lp.trade.mapper.PaymentNotifyAnomalyMapper;
import java.math.BigDecimal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists callbacks that passed verification but could not be applied, each in its own
 * transaction; the unique key makes retries a no-op.
 *
 * @author Caspian
 * @since 0.4.0
 */
@Service
public class PaymentAnomalyRecorder {

    /** Callback amount differs from the local amount due. */
    public static final int AMOUNT_MISMATCH = 1;

    /** Callback order number does not exist locally. */
    public static final int ORDER_MISSING = 2;

    /** Order is paid, closed, cancelled or otherwise not payable. */
    public static final int ORDER_NOT_PAYABLE = 3;

    private final PaymentNotifyAnomalyMapper mapper;

    /**
     * Creates the recorder.
     *
     * @param mapper anomaly persistence
     */
    public PaymentAnomalyRecorder(PaymentNotifyAnomalyMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Records one anomaly in a separate transaction.
     *
     * @param notification parsed callback fields
     * @param expectedAmount local amount due, {@code null} when unknown
     * @param reason one of the reason constants of this class
     * @param detail extra context for operators
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            PaymentNotification notification,
            BigDecimal expectedAmount,
            int reason,
            String detail) {
        try {
            mapper.insert(
                    notification.getChannel(),
                    notification.getOrderNo().trim(),
                    notification.getChannelTradeNo().trim(),
                    expectedAmount,
                    notification.getAmount(),
                    notification.getTradeStatus(),
                    reason,
                    detail);
        } catch (DuplicateKeyException exception) {
            // The same trade and reason were recorded by an earlier retry.
        }
    }
}
