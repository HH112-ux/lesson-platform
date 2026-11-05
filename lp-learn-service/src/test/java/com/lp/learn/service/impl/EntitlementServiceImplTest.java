package com.lp.learn.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.learn.entity.UserEntitlement;
import com.lp.learn.mapper.UserEntitlementMapper;
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
}
