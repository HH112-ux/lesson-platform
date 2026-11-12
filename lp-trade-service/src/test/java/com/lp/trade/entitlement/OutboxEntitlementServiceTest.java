package com.lp.trade.entitlement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.trade.entity.EntitlementOutbox;
import com.lp.trade.mapper.EntitlementOutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * Guards the duplicate-key contract that keeps the refund settlement transaction alive.
 *
 * <p>The revoke insert now runs inside that transaction, so a duplicate escaping here would roll
 * back a refund whose money already left the channel.
 *
 * @author Caspian
 * @since 0.8.0
 */
@ExtendWith(MockitoExtension.class)
class OutboxEntitlementServiceTest {

    @Mock private EntitlementOutboxMapper entitlementOutboxMapper;

    private OutboxEntitlementService service;

    /**
     * Builds the service under test.
     *
     * @author Caspian
     * @since 0.8.0
     */
    @BeforeEach
    void setUp() {
        service = new OutboxEntitlementService(entitlementOutboxMapper);
    }

    /**
     * A duplicate revoke intent is swallowed instead of failing the caller transaction.
     *
     * @author Caspian
     * @since 0.8.0
     */
    @Test
    void duplicateRevokeIntentIsIgnored() {
        when(entitlementOutboxMapper.insert(any(EntitlementOutbox.class)))
                .thenThrow(new DuplicateKeyException("uk_order_course_action"));

        assertDoesNotThrow(() -> service.revoke(1001L, 7L, 8L, "LP20260101000000001"));
        verify(entitlementOutboxMapper).insert(any(EntitlementOutbox.class));
    }
}
