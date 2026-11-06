package com.lp.learn.access;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.learn.config.LearnProperties;
import com.lp.learn.dto.LessonPlayInfo;
import com.lp.learn.service.EntitlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the single lesson access decision point.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class LessonAccessGuardTest {

    private static final long USER_ID = 1001L;

    private static final long COURSE_ID = 7L;

    @Mock private EntitlementService entitlementService;

    private LearnProperties learnProperties;

    private LessonAccessGuard guard;

    /**
     * Builds the guard with the default free preview switch.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        learnProperties = new LearnProperties();
        guard = new LessonAccessGuard(entitlementService, learnProperties);
    }

    /**
     * A free lesson passes while the switch is on, without asking for an entitlement.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void freeLessonPassesWhenPreviewEnabled() {
        assertDoesNotThrow(() -> guard.requireAccess(USER_ID, lesson(true)));
        verify(entitlementService, never()).hasEntitlement(anyLong(), anyLong());
    }

    /**
     * Turning the switch off makes a free lesson entitlement-only.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void freeLessonRejectedWhenPreviewDisabled() {
        learnProperties.setFreePreviewEnabled(false);
        when(entitlementService.hasEntitlement(USER_ID, COURSE_ID)).thenReturn(false);

        BizException exception =
                assertThrows(BizException.class, () -> guard.requireAccess(USER_ID, lesson(true)));

        assertEquals(ErrorCode.LEARN_NOT_ENTITLED.getCode(), exception.getCode());
    }

    /**
     * With the switch off, a purchased free lesson still opens.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void freeLessonPassesWhenPreviewDisabledButPurchased() {
        learnProperties.setFreePreviewEnabled(false);
        when(entitlementService.hasEntitlement(USER_ID, COURSE_ID)).thenReturn(true);

        assertDoesNotThrow(() -> guard.requireAccess(USER_ID, lesson(true)));
    }

    /**
     * A paid lesson always needs an entitlement, regardless of the switch.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void paidLessonRejectedWithoutEntitlement() {
        when(entitlementService.hasEntitlement(USER_ID, COURSE_ID)).thenReturn(false);

        BizException exception =
                assertThrows(BizException.class, () -> guard.requireAccess(USER_ID, lesson(false)));

        assertEquals(ErrorCode.LEARN_NOT_ENTITLED.getCode(), exception.getCode());
    }

    private LessonPlayInfo lesson(boolean free) {
        LessonPlayInfo info = new LessonPlayInfo();
        info.setCourseId(COURSE_ID);
        info.setFree(free);
        return info;
    }
}
