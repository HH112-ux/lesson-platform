package com.lp.learn.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.learn.client.CourseInternalClient;
import com.lp.learn.client.CourseSnapshot;
import com.lp.learn.dto.MyCourseView;
import com.lp.learn.dto.PageView;
import com.lp.learn.entity.UserEntitlement;
import com.lp.learn.mapper.UserEntitlementMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for my-courses; persistence and course-service are mocked.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class MyCourseServiceImplTest {

    private static final long USER_ID = 1001L;

    private static final long COURSE_ID = 7L;

    @Mock private UserEntitlementMapper userEntitlementMapper;

    @Mock private CourseInternalClient courseInternalClient;

    private MyCourseServiceImpl myCourseService;

    /**
     * Builds the service under test.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        myCourseService = new MyCourseServiceImpl(userEntitlementMapper, courseInternalClient);
    }

    /**
     * A student without entitlements gets an empty page and no upstream call.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void noEntitlementReturnsEmptyPage() {
        when(userEntitlementMapper.countActiveByUser(USER_ID)).thenReturn(0L);

        PageView<MyCourseView> page = myCourseService.myCourses(USER_ID, 1, 20);

        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
        verifyNoInteractions(courseInternalClient);
    }

    /**
     * An owned course is joined with its title and cover.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void purchasedCourseReturnsCourseInfo() {
        when(userEntitlementMapper.countActiveByUser(USER_ID)).thenReturn(1L);
        when(userEntitlementMapper.selectActiveByUser(eq(USER_ID), eq(0L), eq(20L)))
                .thenReturn(List.of(entitlement()));
        when(courseInternalClient.courses(anyList()))
                .thenReturn(List.of(new CourseSnapshot(COURSE_ID, "Spring Boot 实战", "cover.png")));

        PageView<MyCourseView> page = myCourseService.myCourses(USER_ID, 1, 20);

        assertEquals(1, page.getTotal());
        assertEquals(1, page.getRecords().size());
        MyCourseView view = page.getRecords().get(0);
        assertEquals(COURSE_ID, view.getCourseId());
        assertEquals("Spring Boot 实战", view.getTitle());
        assertEquals("cover.png", view.getCoverUrl());
        assertEquals(0, view.getProgressPercent());
        assertNull(view.getLastLessonId());
    }

    /**
     * A zero price course is visible as soon as its entitlement exists: price never gates access.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void zeroPriceCourseEntitlementIsVisible() {
        UserEntitlement entitlement = entitlement();
        entitlement.setOrderId(null);
        when(userEntitlementMapper.countActiveByUser(USER_ID)).thenReturn(1L);
        when(userEntitlementMapper.selectActiveByUser(eq(USER_ID), eq(0L), eq(20L)))
                .thenReturn(List.of(entitlement));
        when(courseInternalClient.courses(anyList()))
                .thenReturn(List.of(new CourseSnapshot(COURSE_ID, "免费体验课", "free.png")));

        PageView<MyCourseView> page = myCourseService.myCourses(USER_ID, 1, 20);

        assertEquals(1, page.getRecords().size());
        assertEquals("免费体验课", page.getRecords().get(0).getTitle());
    }

    private UserEntitlement entitlement() {
        UserEntitlement entitlement = new UserEntitlement();
        entitlement.setUserId(USER_ID);
        entitlement.setCourseId(COURSE_ID);
        entitlement.setOrderId(11L);
        entitlement.setSource(1);
        return entitlement;
    }
}
