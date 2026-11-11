package com.lp.trade.service;

import com.lp.common.audit.AuditAction;
import com.lp.common.audit.AuditObject;
import com.lp.common.audit.OperatorRole;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.entity.AuditOutbox;
import com.lp.trade.entity.PaymentReconcile;
import com.lp.trade.entity.PaymentSettleBatch;
import com.lp.trade.mapper.AuditOutboxMapper;
import com.lp.trade.mapper.PaymentReconcileMapper;
import com.lp.trade.mapper.PaymentSettleBatchMapper;
import com.lp.trade.reconcile.SettlementPageView;
import com.lp.trade.reconcile.SettlementView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes one settled bill day of the Alipay channel.
 *
 * <p>A close is a day-end batch seal plus a net summary: it reads the payment and the refund
 * reconciliation aggregates of the day, stores the channel and local totals and their net amount on
 * {@code payment_settle_batch}, and locks the day. It never books a channel fee, never splits a
 * payment, never settles a teacher share and never moves money.
 *
 * <p>Both bills of the day must have been imported first ({@code payment_reconcile.status} 1 to 3),
 * otherwise the call fails with {@code 62019}. A day with pending differences fails with {@code
 * 50204} and writes nothing. The unique key {@code uk_active_day} makes the call idempotent: a
 * repeated close returns the stored batch and its primary key, while a close after a void inserts a
 * new batch and keeps the voided row as history.
 *
 * @author Caspian
 * @since 0.6.2
 */
@Service
public class SettlementService {

    static final int CHANNEL_ALIPAY = 1;

    static final int BILL_TYPE_PAYMENT = 1;

    static final int BILL_TYPE_REFUND = 2;

    static final int STATUS_CLOSED = 1;

    static final int STATUS_VOIDED = 2;

    static final int MAX_PAGE_SIZE = 100;

    private static final int STATUS_IMPORTED_MIN = 1;

    private static final int STATUS_IMPORTED_MAX = 3;

    private static final int MAX_REASON_LENGTH = 300;

    private final PaymentReconcileMapper reconcileMapper;

    private final PaymentSettleBatchMapper settleBatchMapper;

    private final AuditOutboxMapper auditOutboxMapper;

    /**
     * Creates the settlement service.
     *
     * @param reconcileMapper reads the day's payment and refund aggregates
     * @param settleBatchMapper reads and writes the settle batch
     * @param auditOutboxMapper writes the void audit intent in the same transaction
     * @author Caspian
     * @since 0.6.2
     */
    public SettlementService(
            PaymentReconcileMapper reconcileMapper,
            PaymentSettleBatchMapper settleBatchMapper,
            AuditOutboxMapper auditOutboxMapper) {
        this.reconcileMapper = reconcileMapper;
        this.settleBatchMapper = settleBatchMapper;
        this.auditOutboxMapper = auditOutboxMapper;
    }

    /**
     * Closes the bill day and returns the stored batch.
     *
     * @param channel payment channel; only {@link #CHANNEL_ALIPAY} is settled in this release
     * @param billDate bill day in Asia/Shanghai
     * @param closedBy operator user id resolved from the gateway identity header
     * @return closed batch, or the existing batch when the day was already closed
     * @throws BizException with {@code 40001} when the channel or bill day is invalid, {@code
     *     62019} when a bill of the day was not imported, or {@code 50204} when differences are
     *     pending
     * @author Caspian
     * @since 0.6.2
     */
    @Transactional
    public PaymentSettleBatch close(Integer channel, LocalDate billDate, long closedBy) {
        validate(channel, billDate);
        PaymentReconcile payment = requireImported(channel, billDate, BILL_TYPE_PAYMENT);
        PaymentReconcile refund = requireImported(channel, billDate, BILL_TYPE_REFUND);
        if (reconcileMapper.countUnresolved(channel, billDate) > 0) {
            throw new BizException(
                    ErrorCode.RECONCILE_UNRESOLVED,
                    "reconciliation still has unresolved differences");
        }
        PaymentSettleBatch existing = settleBatchMapper.selectActiveByKey(channel, billDate);
        if (existing != null) {
            return existing;
        }
        settleBatchMapper.upsert(build(channel, billDate, closedBy, payment, refund));
        return settleBatchMapper.selectActiveByKey(channel, billDate);
    }

    /**
     * Voids a closed batch, unlocks its bill day and records the reason in the unified audit.
     *
     * <p>The row is kept and marked {@code status=2} with the operator and the time, so the same
     * bill day can be imported and closed again. The close then inserts a new row instead of
     * reviving this one, which keeps every voided batch queryable. Only a closed batch can be
     * voided; voiding twice or voiding a missing batch fails with {@code 40901} or {@code 40401}.
     *
     * <p>The reason is mandatory. It is written to {@code audit_outbox} in the same transaction and
     * relayed to {@code admin_audit_log}, so a void can never happen without an operator, a time
     * and a reason.
     *
     * @param batchId primary key of the batch
     * @param voidBy operator user id resolved from the gateway identity header
     * @param reason why the batch must be voided, required and at most 300 characters
     * @param traceId request trace id resolved by the caller, stored on the audit intent
     * @return voided batch
     * @throws BizException with {@code 40001} when the reason is missing or too long, {@code 40401}
     *     when the batch is missing, or {@code 40901} when it is not closed
     * @author Caspian
     * @since 0.6.2
     */
    @Transactional
    public PaymentSettleBatch voidSettleBatch(
            long batchId, long voidBy, String reason, String traceId) {
        String normalizedReason = requireReason(reason);
        PaymentSettleBatch existing = settleBatchMapper.selectById(batchId);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "settle batch not found");
        }
        if (!Objects.equals(existing.getStatus(), STATUS_CLOSED)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "settle batch is not closed");
        }
        LocalDateTime voidAt = LocalDateTime.now();
        if (settleBatchMapper.voidBatch(batchId, voidBy, voidAt) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "settle batch is not closed");
        }
        auditOutboxMapper.insert(voidAudit(batchId, voidBy, normalizedReason, traceId, voidAt));
        return settleBatchMapper.selectById(batchId);
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "void reason is required");
        }
        String normalized = reason.trim();
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "void reason is too long");
        }
        return normalized;
    }

    private AuditOutbox voidAudit(
            long batchId, long voidBy, String reason, String traceId, LocalDateTime voidAt) {
        AuditOutbox outbox = new AuditOutbox();
        outbox.setDedupeKey("settle-void:" + batchId);
        outbox.setOperatorId(voidBy);
        outbox.setOperatorRole(OperatorRole.ADMIN);
        outbox.setActionType(AuditAction.SETTLE_VOID);
        outbox.setObjectType(AuditObject.SETTLE_BATCH);
        outbox.setObjectId(batchId);
        outbox.setFromStatus(STATUS_CLOSED);
        outbox.setToStatus(STATUS_VOIDED);
        outbox.setRemark(reason);
        outbox.setTraceId(traceId);
        outbox.setOccurredAt(voidAt);
        outbox.setStatus(0);
        outbox.setRetryCount(0);
        return outbox;
    }

    /**
     * Loads one batch by primary key.
     *
     * @param batchId primary key of the batch
     * @return stored batch, whatever its status
     * @throws BizException with {@code 40401} when the batch is missing
     * @author Caspian
     * @since 0.6.2
     */
    public PaymentSettleBatch get(long batchId) {
        PaymentSettleBatch batch = settleBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "settle batch not found");
        }
        return batch;
    }

    /**
     * Lists the batches of one channel ordered by bill day descending.
     *
     * @param channel payment channel; only {@link #CHANNEL_ALIPAY} is settled in this release
     * @param billDate optional bill day filter
     * @param status optional status filter, 1 closed or 2 voided
     * @param pageNum one based page number
     * @param pageSize page size, capped at {@value #MAX_PAGE_SIZE}
     * @return one page of batches
     * @throws BizException with {@code 40001} when the channel or status is invalid
     * @author Caspian
     * @since 0.6.2
     */
    public SettlementPageView page(
            Integer channel, LocalDate billDate, Integer status, int pageNum, int pageSize) {
        if (!Objects.equals(channel, CHANNEL_ALIPAY)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "only Alipay is settled");
        }
        if (status != null
                && !Objects.equals(status, STATUS_CLOSED)
                && !Objects.equals(status, STATUS_VOIDED)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status must be 1 or 2");
        }
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        long total = settleBatchMapper.count(channel, billDate, status);
        List<SettlementView> records =
                settleBatchMapper
                        .page(
                                channel,
                                billDate,
                                status,
                                (safePageNum - 1) * safePageSize,
                                safePageSize)
                        .stream()
                        .map(SettlementView::from)
                        .toList();
        return new SettlementPageView(records, total, safePageNum, safePageSize);
    }

    private void validate(Integer channel, LocalDate billDate) {
        if (!Objects.equals(channel, CHANNEL_ALIPAY)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "only Alipay is settled");
        }
        if (billDate == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bill date is required");
        }
    }

    private PaymentReconcile requireImported(Integer channel, LocalDate billDate, int billType) {
        PaymentReconcile row = reconcileMapper.selectByKey(channel, billDate, billType);
        boolean imported =
                row != null
                        && row.getStatus() >= STATUS_IMPORTED_MIN
                        && row.getStatus() <= STATUS_IMPORTED_MAX;
        if (!imported) {
            throw new BizException(
                    ErrorCode.SETTLE_BILL_INCOMPLETE,
                    billType == BILL_TYPE_PAYMENT
                            ? "payment bill is not imported"
                            : "refund bill is not imported");
        }
        return row;
    }

    private PaymentSettleBatch build(
            Integer channel,
            LocalDate billDate,
            long closedBy,
            PaymentReconcile payment,
            PaymentReconcile refund) {
        PaymentSettleBatch batch = new PaymentSettleBatch();
        batch.setChannel(channel);
        batch.setBillDate(billDate);
        batch.setChannelPayCount(payment.getChannelCount());
        batch.setChannelPayAmount(amount(payment.getChannelAmount()));
        batch.setChannelRefundCount(refund.getChannelCount());
        batch.setChannelRefundAmount(amount(refund.getChannelAmount()));
        batch.setLocalPayCount(payment.getLocalCount());
        batch.setLocalPayAmount(amount(payment.getLocalAmount()));
        batch.setLocalRefundCount(refund.getLocalCount());
        batch.setLocalRefundAmount(amount(refund.getLocalAmount()));
        batch.setNetChannelAmount(
                amount(payment.getChannelAmount().subtract(refund.getChannelAmount())));
        batch.setNetLocalAmount(amount(payment.getLocalAmount().subtract(refund.getLocalAmount())));
        batch.setDiffCount(payment.getDiffCount() + refund.getDiffCount());
        batch.setDiffAmount(amount(payment.getDiffAmount().add(refund.getDiffAmount())));
        batch.setStatus(STATUS_CLOSED);
        batch.setClosedBy(closedBy);
        batch.setClosedAt(LocalDateTime.now());
        return batch;
    }

    private BigDecimal amount(BigDecimal value) {
        return value.setScale(2);
    }
}
