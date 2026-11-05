package com.lp.learn.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.learn.entity.UserEntitlement;
import com.lp.learn.mapper.UserEntitlementMapper;
import com.lp.learn.service.EntitlementService;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link EntitlementService} backed by {@code user_entitlement}.
 *
 * <p>Insertion relies on the unique key instead of a pre-check: a concurrent duplicate is rejected
 * by the database and treated as a successful replay. The trade outbox delivers at-least-once, so
 * this is the normal case, not an error.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class EntitlementServiceImpl implements EntitlementService {

    private final UserEntitlementMapper userEntitlementMapper;

    /**
     * Creates the service with its mapper dependency.
     *
     * @param userEntitlementMapper mapper reading {@code user_entitlement}
     * @author Caspian
     * @since 0.1.0
     */
    public EntitlementServiceImpl(UserEntitlementMapper userEntitlementMapper) {
        this.userEntitlementMapper = userEntitlementMapper;
    }

    /**
     * Grants one course to one student, ignoring a duplicate delivery.
     *
     * @param userId student user id
     * @param courseId course id
     * @param orderId order id, {@code null} for operations-issued access
     * @param source 1 purchase, 2 operations grant, 3 redemption
     * @throws BizException when a required field is missing
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grant(long userId, long courseId, Long orderId, int source) {
        if (userId <= 0 || courseId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId and courseId are required");
        }
        UserEntitlement entitlement = new UserEntitlement();
        entitlement.setUserId(userId);
        entitlement.setCourseId(courseId);
        entitlement.setOrderId(orderId);
        entitlement.setSource(source);
        entitlement.setGrantedAt(LocalDateTime.now());
        try {
            userEntitlementMapper.insert(entitlement);
        } catch (DuplicateKeyException exception) {
            // The unique key uk_user_course already holds the access right; replay is a no-op.
        }
    }

    /**
     * Tells whether the student currently owns the course.
     *
     * @param userId student user id
     * @param courseId course id
     * @return {@code true} when an active entitlement exists
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public boolean hasEntitlement(long userId, long courseId) {
        return userEntitlementMapper.countActive(userId, courseId) > 0;
    }
}
