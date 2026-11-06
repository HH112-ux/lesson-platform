package com.lp.learn.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.learn.domain.EntitlementStatus;
import com.lp.learn.entity.UserEntitlement;
import com.lp.learn.mapper.UserEntitlementMapper;
import com.lp.learn.service.EntitlementService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link EntitlementService} backed by {@code user_entitlement}.
 *
 * <p>Grant and revoke both lock the {@code userId + courseId} row and decide on its current state,
 * which keeps the two actions ordered even when the trade outbox delivers them out of order:
 *
 * <ul>
 *   <li>a grant for an already refunded order leaves the row revoked, so a delayed payment message
 *       cannot resurrect access for money that was returned;
 *   <li>a grant for a new order reactivates the refunded row, so a repurchase works without a
 *       second row;
 *   <li>a revoke for an older order is ignored when a newer purchase already owns the course.
 * </ul>
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class EntitlementServiceImpl implements EntitlementService {

    private static final int SOURCE_PURCHASE = 1;

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
        LocalDateTime now = LocalDateTime.now();
        UserEntitlement existing =
                userEntitlementMapper.selectByUserCourseForUpdate(userId, courseId);
        if (existing == null) {
            UserEntitlement entitlement = new UserEntitlement();
            entitlement.setUserId(userId);
            entitlement.setCourseId(courseId);
            entitlement.setOrderId(orderId);
            entitlement.setSource(source);
            entitlement.setStatus(EntitlementStatus.ACTIVE);
            entitlement.setGrantedAt(now);
            try {
                userEntitlementMapper.insert(entitlement);
                return;
            } catch (DuplicateKeyException exception) {
                existing = userEntitlementMapper.selectByUserCourseForUpdate(userId, courseId);
            }
        }
        if (existing == null || !Objects.equals(existing.getStatus(), EntitlementStatus.REVOKED)) {
            // An active row already holds the right; replay is a no-op.
            return;
        }
        if (orderId != null && orderId.equals(existing.getOrderId())) {
            // Delayed grant for the very order that was refunded; it must not restore access.
            return;
        }
        userEntitlementMapper.reactivate(existing.getId(), orderId, source, now);
    }

    /**
     * Revokes access after a refund, keeping the row and its learning records.
     *
     * @param userId student user id
     * @param courseId course id
     * @param orderId order that was refunded, {@code null} when unknown
     * @throws BizException when a required field is missing
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(long userId, long courseId, Long orderId) {
        if (userId <= 0 || courseId <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "userId and courseId are required");
        }
        LocalDateTime now = LocalDateTime.now();
        UserEntitlement existing =
                userEntitlementMapper.selectByUserCourseForUpdate(userId, courseId);
        if (existing == null) {
            UserEntitlement revoked = new UserEntitlement();
            revoked.setUserId(userId);
            revoked.setCourseId(courseId);
            revoked.setOrderId(orderId);
            revoked.setSource(SOURCE_PURCHASE);
            revoked.setStatus(EntitlementStatus.REVOKED);
            revoked.setGrantedAt(now);
            revoked.setRevokedAt(now);
            try {
                // The payment grant may still be in flight; the revoked placeholder makes the
                // later grant a no-op instead of a second insert.
                userEntitlementMapper.insert(revoked);
                return;
            } catch (DuplicateKeyException exception) {
                existing = userEntitlementMapper.selectByUserCourseForUpdate(userId, courseId);
            }
        }
        if (existing == null || Objects.equals(existing.getStatus(), EntitlementStatus.REVOKED)) {
            return;
        }
        if (orderId != null
                && existing.getOrderId() != null
                && !orderId.equals(existing.getOrderId())) {
            // A newer purchase already owns the course; the refunded order is older.
            return;
        }
        userEntitlementMapper.revoke(existing.getId(), now);
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

    /**
     * Returns the subset of the given course ids the student actively owns.
     *
     * @param userId student user id
     * @param courseIds candidate course ids
     * @return owned ids, empty when the input is empty or nothing matches
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public List<Long> ownedCourseIds(long userId, List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        return userEntitlementMapper.selectActiveCourseIds(userId, courseIds);
    }
}
