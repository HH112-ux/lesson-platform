package com.lp.course.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.course.dto.TeacherAuditApplicationView;
import com.lp.course.dto.TeacherAuditPageView;
import com.lp.course.dto.TeacherAuditResultView;
import com.lp.course.entity.TeacherProfileAudit;
import com.lp.course.mapper.TeacherAuditMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for teacher onboarding review rules.
 *
 * @author Caspian
 * @since 0.5.0
 */
@ExtendWith(MockitoExtension.class)
class TeacherAuditServiceImplTest {

    @Mock private TeacherAuditMapper teacherAuditMapper;

    private TeacherAuditServiceImpl service;

    /**
     * Builds the service under test with a mocked mapper.
     *
     * @author Caspian
     * @since 0.5.0
     */
    @BeforeEach
    void setUp() {
        service = new TeacherAuditServiceImpl(teacherAuditMapper);
    }

    /**
     * The queue returns the requested page together with the unfiltered total.
     *
     * @author Caspian
     * @since 0.5.0
     */
    @Test
    void listsPendingApplications() {
        when(teacherAuditMapper.count(0, null)).thenReturn(1L);
        when(teacherAuditMapper.selectPage(0, null, 0L, 20L)).thenReturn(List.of(profile(7L, 0)));

        TeacherAuditPageView page = service.page(0, null, 1, 20);

        assertEquals(1L, page.total());
        assertEquals(7L, page.records().get(0).teacherId());
        assertEquals(0, page.records().get(0).auditStatus());
    }

    /**
     * An audit status outside {@code 0..2} is a parameter error.
     *
     * @author Caspian
     * @since 0.5.0
     */
    @Test
    void rejectsUnknownAuditStatusFilter() {
        BizException exception =
                assertThrows(BizException.class, () -> service.page(3, null, 1, 20));

        assertEquals(40001, exception.getCode());
        verify(teacherAuditMapper, never()).count(any(), any());
    }

    /**
     * Rejecting without a reason is refused and no decision is written.
     *
     * @author Caspian
     * @since 0.5.0
     */
    @Test
    void requiresRemarkOnRejection() {
        when(teacherAuditMapper.selectById(7L)).thenReturn(profile(7L, 0));

        BizException exception =
                assertThrows(BizException.class, () -> service.review(7L, false, "  ", 900L));

        assertEquals(40001, exception.getCode());
        verify(teacherAuditMapper, never())
                .applyReview(anyLong(), anyInt(), any(), anyLong(), any());
    }

    /**
     * Approval records the operator and moves the profile to approved.
     *
     * @author Caspian
     * @since 0.5.0
     */
    @Test
    void approvesPendingApplication() {
        when(teacherAuditMapper.selectById(7L)).thenReturn(profile(7L, 0));
        when(teacherAuditMapper.applyReview(eq(7L), eq(1), isNull(), eq(900L), any()))
                .thenReturn(1);

        TeacherAuditResultView result = service.review(7L, true, null, 900L);

        assertEquals(1, result.auditStatus());
        assertNull(result.auditRemark());
        assertEquals(900L, result.auditedBy());
    }

    /**
     * A profile that is already decided cannot be reviewed again.
     *
     * @author Caspian
     * @since 0.5.0
     */
    @Test
    void rejectsReviewOutsidePendingState() {
        when(teacherAuditMapper.selectById(7L)).thenReturn(profile(7L, 1));

        BizException exception =
                assertThrows(BizException.class, () -> service.review(7L, false, "bad", 900L));

        assertEquals(40901, exception.getCode());
    }

    /**
     * A rejected teacher resubmits and returns to the pending queue.
     *
     * @author Caspian
     * @since 0.5.0
     */
    @Test
    void resubmitsRejectedApplication() {
        when(teacherAuditMapper.selectByUserId(1001L))
                .thenReturn(profile(7L, 2))
                .thenReturn(profile(7L, 0));
        when(teacherAuditMapper.resubmit(eq(1001L), any(LocalDateTime.class))).thenReturn(1);

        TeacherAuditApplicationView view = service.resubmit(1001L, "TEACHER");

        assertEquals(0, view.auditStatus());
        verify(teacherAuditMapper).resubmit(eq(1001L), any(LocalDateTime.class));
    }

    /**
     * An approved teacher cannot put the application back into review.
     *
     * @author Caspian
     * @since 0.5.0
     */
    @Test
    void refusesResubmitWhenAlreadyApproved() {
        when(teacherAuditMapper.selectByUserId(1001L)).thenReturn(profile(7L, 1));

        BizException exception =
                assertThrows(BizException.class, () -> service.resubmit(1001L, "TEACHER"));

        assertEquals(40901, exception.getCode());
        verify(teacherAuditMapper, never()).resubmit(anyLong(), any());
    }

    /**
     * Builds a teacher profile row.
     *
     * @param teacherId profile primary key
     * @param auditStatus onboarding audit status
     * @return row
     * @author Caspian
     * @since 0.5.0
     */
    private TeacherProfileAudit profile(long teacherId, int auditStatus) {
        TeacherProfileAudit row = new TeacherProfileAudit();
        row.setId(teacherId);
        row.setUserId(1001L);
        row.setDisplayName("Teacher " + teacherId);
        row.setStatus(1);
        row.setAuditStatus(auditStatus);
        row.setSubmittedAt(LocalDateTime.of(2026, 11, 9, 10, 0));
        row.setCourseCount(2L);
        return row;
    }
}
