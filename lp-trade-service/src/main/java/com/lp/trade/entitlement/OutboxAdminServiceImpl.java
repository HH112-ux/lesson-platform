package com.lp.trade.entitlement;

import com.lp.trade.dto.FailedOutboxPage;
import com.lp.trade.dto.FailedOutboxView;
import com.lp.trade.entity.EntitlementOutbox;
import com.lp.trade.mapper.EntitlementOutboxMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Default {@link OutboxAdminService} implementation.
 *
 * <p>Retrying only flips a given up row back to pending; the regular relay then delivers it, so the
 * manual path shares the same retry, backoff and idempotency as the automatic one.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class OutboxAdminServiceImpl implements OutboxAdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 100;

    private static final int GIVEN_UP_STATUS = 2;

    private final EntitlementOutboxMapper entitlementOutboxMapper;

    private final EntitlementOutboxMetrics metrics;

    /**
     * Creates the service with its dependencies.
     *
     * @param entitlementOutboxMapper outbox persistence
     * @param metrics gauges refreshed after a manual retry
     * @author Caspian
     * @since 0.1.0
     */
    public OutboxAdminServiceImpl(
            EntitlementOutboxMapper entitlementOutboxMapper, EntitlementOutboxMetrics metrics) {
        this.entitlementOutboxMapper = entitlementOutboxMapper;
        this.metrics = metrics;
    }

    /**
     * Lists given up intents, newest first.
     *
     * @param pageNum one based page number
     * @param pageSize requested page size
     * @return one page of given up intents
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public FailedOutboxPage failed(int pageNum, int pageSize) {
        int safePageNum = pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        long total = entitlementOutboxMapper.countByStatus(GIVEN_UP_STATUS);
        if (total == 0) {
            return new FailedOutboxPage(safePageNum, safePageSize, 0, List.of());
        }
        List<FailedOutboxView> records =
                entitlementOutboxMapper
                        .selectGivenUp((long) (safePageNum - 1) * safePageSize, safePageSize)
                        .stream()
                        .map(this::toView)
                        .toList();
        return new FailedOutboxPage(safePageNum, safePageSize, total, records);
    }

    /**
     * Puts one given up intent back into the pending queue.
     *
     * @param id outbox id
     * @return {@code true} when the row was given up and is now pending again
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public boolean retry(long id) {
        boolean reset = entitlementOutboxMapper.resetForRetry(id) > 0;
        if (reset) {
            metrics.refresh();
        }
        return reset;
    }

    private FailedOutboxView toView(EntitlementOutbox outbox) {
        return new FailedOutboxView(
                outbox.getId(),
                outbox.getOrderId(),
                outbox.getCourseId(),
                outbox.getUserId(),
                outbox.getRetryCount() == null ? 0 : outbox.getRetryCount(),
                outbox.getLastError(),
                outbox.getUpdatedAt());
    }
}
