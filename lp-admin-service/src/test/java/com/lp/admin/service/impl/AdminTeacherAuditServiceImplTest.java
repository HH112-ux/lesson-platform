package com.lp.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.admin.client.TeacherAuditClient;
import com.lp.admin.dto.AdminTeacherPageView;
import com.lp.admin.dto.AdminTeacherReviewResultView;
import com.lp.common.exception.BizException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests of the teacher onboarding console rules.
 *
 * @author Caspian
 * @since 0.5.0
 */
@ExtendWith(MockitoExtension.class)
class AdminTeacherAuditServiceImplTest {

    @Mock private TeacherAuditClient teacherAuditClient;

    @InjectMocks private AdminTeacherAuditServiceImpl service;

    /**
     * Paging bounds are clamped and a blank keyword is dropped before the call.
     *
     * @author Caspian
     * @since 0.5.0
     */
    @Test
    void clampsPagingBeforeDelegating() {
        when(teacherAuditClient.page(0, null, 1, 100))
                .thenReturn(new AdminTeacherPageView(List.of(), 0, 1, 100));

        AdminTeacherPageView page = service.page(0, "  ", 0, 500);

        assertEquals(1, page.pageNum());
        assertEquals(100, page.pageSize());
        verify(teacherAuditClient).page(0, null, 1, 100);
    }

    /**
     * An audit status outside {@code 0..2} is refused before the call.
     *
     * @author Caspian
     * @since 0.5.0
     */
    @Test
    void rejectsUnknownAuditStatusFilter() {
        BizException exception =
                assertThrows(BizException.class, () -> service.page(9, null, 1, 20));

        assertEquals(40001, exception.getCode());
        verify(teacherAuditClient, never()).page(any(), any(), anyInt(), anyInt());
    }

    /**
     * Approval is forwarded with the operator id.
     *
     * @author Caspian
     * @since 0.5.0
     */
    @Test
    void forwardsApproval() {
        when(teacherAuditClient.review(7L, true, null, 55L))
                .thenReturn(new AdminTeacherReviewResultView(7L, 1, null, 55L, null));

        AdminTeacherReviewResultView result = service.review(7L, true, null, 55L);

        assertEquals(1, result.auditStatus());
        assertEquals(55L, result.auditedBy());
    }

    /**
     * A rejection without a reason never reaches course-service.
     *
     * @author Caspian
     * @since 0.5.0
     */
    @Test
    void rejectsBlankRemark() {
        BizException exception =
                assertThrows(BizException.class, () -> service.review(7L, false, "  ", 55L));

        assertEquals(40001, exception.getCode());
        verify(teacherAuditClient, never()).review(anyLong(), anyBoolean(), anyString(), anyLong());
    }

    /**
     * A rejection remark is trimmed and capped at the column length.
     *
     * @author Caspian
     * @since 0.5.0
     */
    @Test
    void trimsAndCapsRejectionRemark() {
        String remark = "  " + "x".repeat(400);
        when(teacherAuditClient.review(eq(7L), eq(false), anyString(), eq(55L)))
                .thenReturn(new AdminTeacherReviewResultView(7L, 2, "x".repeat(300), 55L, null));

        service.review(7L, false, remark, 55L);

        verify(teacherAuditClient).review(7L, false, "x".repeat(300), 55L);
    }
}
