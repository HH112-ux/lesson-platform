package com.lp.learn.service.impl;

import com.lp.learn.client.CourseInternalClient;
import com.lp.learn.client.CourseSnapshot;
import com.lp.learn.dto.MyCourseView;
import com.lp.learn.dto.PageView;
import com.lp.learn.entity.UserEntitlement;
import com.lp.learn.mapper.UserEntitlementMapper;
import com.lp.learn.service.MyCourseService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Default {@link MyCourseService} implementation.
 *
 * <p>Entitlements are the source of truth for access; course titles and covers come from the
 * course-service internal batch API. Entitlements whose course disappeared are skipped instead of
 * failing the whole page.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class MyCourseServiceImpl implements MyCourseService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 100;

    private final UserEntitlementMapper userEntitlementMapper;

    private final CourseInternalClient courseInternalClient;

    /**
     * Creates the service with its dependencies.
     *
     * @param userEntitlementMapper entitlement persistence
     * @param courseInternalClient course display reader
     * @author Caspian
     * @since 0.1.0
     */
    public MyCourseServiceImpl(
            UserEntitlementMapper userEntitlementMapper,
            CourseInternalClient courseInternalClient) {
        this.userEntitlementMapper = userEntitlementMapper;
        this.courseInternalClient = courseInternalClient;
    }

    /**
     * Returns one page of courses the student may access.
     *
     * @param userId student user id
     * @param pageNum one based page number
     * @param pageSize requested page size
     * @return paged courses; empty when the student owns nothing
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public PageView<MyCourseView> myCourses(long userId, int pageNum, int pageSize) {
        int safePageNum = pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        long total = userEntitlementMapper.countActiveByUser(userId);
        if (total == 0) {
            return new PageView<>(safePageNum, safePageSize, 0, List.of());
        }

        List<UserEntitlement> entitlements =
                userEntitlementMapper.selectActiveByUser(
                        userId, (long) (safePageNum - 1) * safePageSize, safePageSize);
        if (entitlements.isEmpty()) {
            return new PageView<>(safePageNum, safePageSize, total, List.of());
        }

        List<Long> courseIds =
                entitlements.stream().map(UserEntitlement::getCourseId).distinct().toList();
        Map<Long, CourseSnapshot> courses =
                courseInternalClient.courses(courseIds).stream()
                        .collect(
                                Collectors.toMap(CourseSnapshot::getCourseId, Function.identity()));
        List<MyCourseView> records =
                entitlements.stream()
                        .map(
                                entitlement -> {
                                    CourseSnapshot course = courses.get(entitlement.getCourseId());
                                    if (course == null) {
                                        return null;
                                    }
                                    return new MyCourseView(
                                            course.getCourseId(),
                                            course.getTitle(),
                                            course.getCoverUrl(),
                                            0,
                                            null);
                                })
                        .filter(view -> view != null)
                        .toList();
        return new PageView<>(safePageNum, safePageSize, total, records);
    }
}
