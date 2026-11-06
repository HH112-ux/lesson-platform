package com.lp.learn.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.learn.domain.EntitlementStatus;
import com.lp.learn.entity.UserEntitlement;
import com.lp.learn.mapper.UserEntitlementMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * Unit tests for entitlement granting; persistence is mocked.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class EntitlementServiceImplTest {

    private static final long USER_ID = 1001L;

    private static final long COURSE_ID = 7L;

    private static final long ORDER_ID = 1L;

    @Mock private UserEntitlementMapper userEntitlementMapper;

    private EntitlementServiceImpl entitlementService;

    /**
     * Builds the service under test.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        entitlementService = new EntitlementServiceImpl(userEntitlementMapper);
    }

    /**
     * The first grant inserts the purchase row with its source and order.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void firstGrantInsertsPurchaseRow() {
        when(userEntitlementMapper.insert(any(UserEntitlement.class))).thenReturn(1);

        entitlementService.grant(USER_ID, COURSE_ID, ORDER_ID, 1);

        ArgumentCaptor<UserEntitlement> captor = ArgumentCaptor.forClass(UserEntitlement.class);
        verify(userEntitlementMapper).insert(captor.capture());
        UserEntitlement saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(COURSE_ID, saved.getCourseId());
        assertEquals(ORDER_ID, saved.getOrderId());
        assertEquals(1, saved.getSource());
        assertNotNull(saved.getGrantedAt());
    }

    /**
     * A repeated delivery rejected by the unique key is treated as success.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void duplicateGrantIsIdempotent() {
        when(userEntitlementMapper.insert(any(UserEntitlement.class)))
                .thenThrow(new DuplicateKeyException("uk_user_course"));

        assertDoesNotThrow(() -> entitlementService.grant(USER_ID, COURSE_ID, ORDER_ID, 1));
        verify(userEntitlementMapper).insert(any(UserEntitlement.class));
    }

    /**
     * Ownership is answered by the active entitlement count.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void hasEntitlementReadsActiveCount() {
        when(userEntitlementMapper.countActive(USER_ID, COURSE_ID)).thenReturn(1L);

        assertTrue(entitlementService.hasEntitlement(USER_ID, COURSE_ID));
    }

    /**
     * Revoking an active entitlement flips it to revoked and keeps the row.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void revokeActiveRowKeepsRowAndMarksRevoked() {
        when(userEntitlementMapper.selectByUserCourseForUpdate(USER_ID, COURSE_ID))
                .thenReturn(active(ORDER_ID));

        entitlementService.revoke(USER_ID, COURSE_ID, ORDER_ID);

        verify(userEntitlementMapper).revoke(eq(9L), any());
    }

    /**
     * A revoke that arrives before the grant writes a revoked placeholder, so the later grant is a
     * no-op instead of restoring access for refunded money.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void revokeBeforeGrantInsertsRevokedPlaceholder() {
        when(userEntitlementMapper.insert(any(UserEntitlement.class))).thenReturn(1);

        entitlementService.revoke(USER_ID, COURSE_ID, ORDER_ID);

        ArgumentCaptor<UserEntitlement> captor = ArgumentCaptor.forClass(UserEntitlement.class);
        verify(userEntitlementMapper).insert(captor.capture());
        UserEntitlement saved = captor.getValue();
        assertEquals(EntitlementStatus.REVOKED, saved.getStatus().intValue());
        assertEquals(ORDER_ID, saved.getOrderId());
        assertNotNull(saved.getRevokedAt());
    }

    /**
     * A delayed grant for the refunded order must not resurrect the revoked entitlement.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void grantAfterRefundForSameOrderStaysRevoked() {
        when(userEntitlementMapper.selectByUserCourseForUpdate(USER_ID, COURSE_ID))
                .thenReturn(revoked(ORDER_ID));

        entitlementService.grant(USER_ID, COURSE_ID, ORDER_ID, 1);

        verify(userEntitlementMapper, never()).insert(any(UserEntitlement.class));
        verify(userEntitlementMapper, never()).reactivate(anyLong(), any(), anyInt(), any());
    }

    /**
     * Buying the course again reactivates the same refunded row with the new order.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void grantForNewOrderReactivatesRefundedRow() {
        when(userEntitlementMapper.selectByUserCourseForUpdate(USER_ID, COURSE_ID))
                .thenReturn(revoked(ORDER_ID));

        entitlementService.grant(USER_ID, COURSE_ID, 2L, 1);

        verify(userEntitlementMapper).reactivate(eq(9L), eq(2L), eq(1), any());
    }

    /**
     * Refunding an older order must not revoke access bought again later.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void revokeOlderOrderLeavesNewerPurchaseActive() {
        when(userEntitlementMapper.selectByUserCourseForUpdate(USER_ID, COURSE_ID))
                .thenReturn(active(2L));

        entitlementService.revoke(USER_ID, COURSE_ID, ORDER_ID);

        verify(userEntitlementMapper, never()).revoke(anyLong(), any());
        verify(userEntitlementMapper, never()).insert(any(UserEntitlement.class));
    }

    private UserEntitlement active(long orderId) {
        return entitlement(EntitlementStatus.ACTIVE, orderId);
    }

    private UserEntitlement revoked(long orderId) {
        UserEntitlement row = entitlement(EntitlementStatus.REVOKED, orderId);
        row.setRevokedAt(LocalDateTime.now());
        return row;
    }

    private UserEntitlement entitlement(int status, long orderId) {
        UserEntitlement row = new UserEntitlement();
        row.setId(9L);
        row.setUserId(USER_ID);
        row.setCourseId(COURSE_ID);
        row.setOrderId(orderId);
        row.setSource(1);
        row.setStatus(status);
        row.setGrantedAt(LocalDateTime.now().minusDays(1));
        return row;
    }
}
