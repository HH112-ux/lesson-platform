package com.lp.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.admin.client.CourseReviewClient;
import com.lp.admin.dto.AdminPendingCoursePageView;
import com.lp.admin.dto.AdminReviewResultView;
import com.lp.common.exception.BizException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests of the review use cases.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class AdminCourseReviewServiceImplTest {

    @Mock private CourseReviewClient courseReviewClient;

    @InjectMocks private AdminCourseReviewServiceImpl service;

    /**
     * Paging bounds are clamped before the course-service call.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void clampsPagingBeforeDelegating() {
        when(courseReviewClient.pending(1, 100))
                .thenReturn(new AdminPendingCoursePageView(List.of(), 0, 1, 100));

        AdminPendingCoursePageView page = service.pending(0, 500);

        assertEquals(1, page.pageNum());
        assertEquals(100, page.pageSize());
        verify(courseReviewClient).pending(1, 100);
    }

    /**
     * Approval is forwarded with the operator id.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void forwardsApproval() {
        when(courseReviewClient.review(7L, true, 9900L, null, 55L))
                .thenReturn(new AdminReviewResultView(7L, 2, 9900L, null));

        AdminReviewResultView result = service.review(7L, true, 9900L, null, 55L);

        assertEquals(2, result.status());
        assertTrue(result.priceFen() == 9900L);
    }

    /**
     * A rejection without a remark never reaches course-service.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsBlankRemark() {
        BizException exception =
                assertThrows(BizException.class, () -> service.review(7L, false, null, "  ", 55L));

        assertEquals(40001, exception.getCode());
        assertFalse(exception.getMessage().isBlank());
    }
}
